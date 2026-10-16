import os, time, uuid, asyncio
from collections import deque
from contextlib import asynccontextmanager
from dataclasses import dataclass
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field
from aiogram import Bot, Dispatcher, Router
from aiogram.types import Message, Update
from aiogram.filters import Command

load_dotenv(); BOT_TOKEN=os.environ["BOT_TOKEN"]; ADMIN=int(os.environ["ADMIN_TELEGRAM_ID"])
DEVICE_TOKEN=os.environ["DEVICE_TOKEN"]; WEBHOOK_SECRET=os.environ["WEBHOOK_SECRET"]
ALLOWED={x.strip() for x in os.getenv("ALLOWED_SMS_NUMBERS","").split(",") if x.strip()}
MAIN=os.getenv("MAIN_PHONE_NUMBER",""); bot=Bot(BOT_TOKEN); dp=Dispatcher(); router=Router(); dp.include_router(router)
outbox=deque(); delivered=set(); seen_incoming=set(); errors=deque(maxlen=100); device_last=0.0; lock=asyncio.Lock()
@dataclass
class Job: id:str; text:str; created:float; target:str; leased:float=0
class TextIn(BaseModel): text:str=Field(min_length=1,max_length=10000); sender:str; external_id:str
class Ack(BaseModel): message_id:str; success:bool=True; error:str|None=None
def auth(token):
    if token!=DEVICE_TOKEN: raise HTTPException(401,"invalid device token")
def number(value:str)->str:
    value="".join(c for c in value if c.isdigit() or c=="+")
    return value if value.startswith("+") else "+"+value
def allowed_user(m:Message): return m.from_user and m.from_user.id==ADMIN
@router.message(Command("start"))
async def start(m:Message):
    if allowed_user(m): await m.answer("Мост подключён. Отправьте текст для SMS.")
    else: await m.answer("Доступ запрещён.")
@router.message(Command("status"))
async def status(m:Message):
    if allowed_user(m): await m.answer(f"Шлюз: {'онлайн' if time.time()-device_last<180 else 'не в сети'}; очередь: {len(outbox)}")
@router.message()
async def text(m:Message):
    if not allowed_user(m): await m.answer("Доступ запрещён."); return
    if not m.text: return
    async with lock: outbox.append(Job(str(uuid.uuid4()),m.text,time.time(),MAIN))
    await m.answer("Сообщение поставлено в очередь SMS.")
@asynccontextmanager
async def life(app): yield; await bot.session.close()
app=FastAPI(title="SMS Telegram Bridge",lifespan=life)
@app.post("/telegram/webhook/{secret}")
async def webhook(secret:str, request:Request):
    if secret!=WEBHOOK_SECRET: raise HTTPException(404)
    try: await dp.feed_update(bot,Update.model_validate(await request.json()))
    except Exception: raise HTTPException(400,"bad update")
    return {"ok":True}
@app.post("/device/register")
async def register(x_token:str=Header(default="")): auth(x_token); return {"ok":True,"server_time":time.time()}
@app.get("/device/queue")
async def queue(x_token:str=Header(default="")):
    global device_last; auth(x_token); device_last=time.time(); now=time.time(); result=[]
    async with lock:
        for j in list(outbox):
            if not j.leased or now-j.leased>120: j.leased=now; result.append({"id":j.id,"text":j.text,"target":j.target})
    return {"messages":result[:20]}
@app.post("/device/ack")
async def ack(a:Ack,x_token:str=Header(default="")):
    global device_last; auth(x_token); device_last=time.time()
    async with lock:
        for j in list(outbox):
            if j.id==a.message_id:
                if a.success: outbox.remove(j); delivered.add(j.id)
                else: j.leased=0; errors.append((j.id,a.error or "sms failure"))
                break
    return {"ok":True}
@app.post("/device/incoming")
async def incoming(x:TextIn,x_token:str=Header(default="")):
    global device_last; auth(x_token); device_last=time.time()
    if number(x.sender) not in {number(n) for n in ALLOWED} or x.external_id in seen_incoming: return {"ok":True}
    seen_incoming.add(x.external_id)
    await bot.send_message(ADMIN, x.text)
    return {"ok":True}
@app.get("/health")
async def health(): return {"ok":True,"device_online":time.time()-device_last<180,"queue":len(outbox)}
