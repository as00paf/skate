import math, struct, sys, lameenc
def enc(path, rate, channels, seconds, br, id3=False):
    pcm=bytearray()
    for i in range(int(rate*seconds)):
        l=int(math.sin(2*math.pi*440*i/rate)*8000); r=int(math.sin(2*math.pi*660*i/rate)*8000)
        pcm+=struct.pack('<h',l) if channels==1 else struct.pack('<hh',l,r)
    e=lameenc.Encoder(); e.set_bit_rate(br); e.set_in_sample_rate(rate); e.set_channels(channels); e.set_quality(2)
    data=e.encode(bytes(pcm))+e.flush()
    if id3:
        def ss(n): return bytes([(n>>21)&0x7f,(n>>14)&0x7f,(n>>7)&0x7f,n&0x7f])
        t=b'\x03Skate test tone'; body=b'TIT2'+struct.pack('>I',len(t))+b'\x00\x00'+t
        pic=b'\x00image/png\x00\x03\x00'+b'\x00'*1024; body+=b'APIC'+struct.pack('>I',len(pic))+b'\x00\x00'+pic
        data=b'ID3\x03\x00\x00'+ss(len(body))+body+data
    open(path,'wb').write(data); print(path,len(data))
d=sys.argv[1]
enc(d+'/tone-stereo-44k.mp3',44100,2,0.25,128,id3=True)
enc(d+'/tone-mono-44k.mp3',44100,1,0.25,64)
enc(d+'/tone-mono-22k-mpeg2.mp3',22050,1,0.25,32)
