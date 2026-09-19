"""Original Pounce melody and synthetic kitten accents, no network assets."""
from pathlib import Path
import numpy as np
import wave, json
root=Path(__file__).resolve().parents[1]
out=root/"app/src/main/res/raw"; out.mkdir(parents=True,exist_ok=True)
sr=44100; seconds=16; t=np.arange(sr*seconds)/sr
mix=np.zeros(len(t),dtype=np.float64)
def add(signal, start, gain=1):
 i=int(start*sr); n=min(len(signal),len(mix)-i)
 if n>0: mix[i:i+n]+=signal[:n]*gain
def tone(note,duration=.48):
 q=np.arange(int(sr*duration))/sr
 f=440*2**((note-69)/12)
 env=(1-np.exp(-q*70))*np.exp(-q*5)*np.clip((duration-q)*25,0,1)
 return (.8*np.sin(2*np.pi*f*q)+.16*np.sin(2*np.pi*f*2*q)+.04*np.sin(2*np.pi*f*3*q))*env
notes=[64,67,69,67,64,62,60,62,64,67,72,71,69,67,64,62]
for j,n in enumerate(notes*2):
 start=j*.5
 add(tone(n,.8),start,.20)
 if j%4==0: add(tone(n-12,1.4),start,.11)
 if j>=16 and j%2==0: add(tone(n+7,.6),start,.035)
def mew():
 q=np.arange(int(sr*.75))/sr
 f=520+430*np.sin(np.pi*np.clip(q/.75,0,1))**1.5-160*q
 phase=2*np.pi*np.cumsum(f)/sr
 env=np.sin(np.pi*q/.75)**1.6
 return env*(np.sin(phase)+.2*np.sin(2*phase)+.07*np.sin(3*phase))
recovered=root/"assets/audio/kitten_meows.wav"
if recovered.exists():
 with wave.open(str(recovered),"rb") as source:
  channels=source.getnchannels(); original_rate=source.getframerate()
  assert source.getsampwidth()==2
  cat=np.frombuffer(source.readframes(int(original_rate*1.9)),dtype="<i2").astype(np.float64)/32768
  if channels>1: cat=cat.reshape(-1,channels).mean(axis=1)
  cat=np.interp(np.arange(int(len(cat)*sr/original_rate))*original_rate/sr,np.arange(len(cat)),cat)
  cat*=np.minimum(np.arange(len(cat))/sr/.02,1)*np.minimum(np.arange(len(cat))[::-1]/sr/.04,1)
  peak=np.max(abs(cat))
  if peak>0: cat/=peak
 add(cat,.02,.18);add(cat,8.02,.11)
else:
 add(mew(),.07,.12);add(mew(),8.1,.07)
mix*=np.minimum(t/.02,1)*np.minimum((seconds-t)/.07,1)
mix=np.clip(mix,-.85,.85)
with wave.open(str(out/"pounce_melody.wav"),"wb") as w:
 w.setnchannels(1);w.setsampwidth(2);w.setframerate(sr);w.writeframes((mix*32767).astype("<i2").tobytes())
checks={"duration_seconds":seconds,"peak_dbfs":float(20*np.log10(np.max(abs(mix)))),"clipped_samples":int(np.sum(abs(mix)>=1)),"original_composition":True}
(root/"artifacts/audio-checks.json").write_text(json.dumps(checks,indent=2))
print(json.dumps(checks))
