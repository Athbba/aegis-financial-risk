#!/usr/bin/env python3
"""Convert local docker-save output to an OCI layout archive without publishing images."""
import hashlib,io,json,pathlib,subprocess,sys,tarfile,tempfile,gzip
image,destination=sys.argv[1:]
with tempfile.TemporaryDirectory() as tmp:
    source=pathlib.Path(tmp)/'image.tar'
    subprocess.run(['docker','image','save','-o',str(source),image],check=True)
    with tarfile.open(source) as incoming,tarfile.open(destination,'w') as outgoing:
        emitted=set()
        def put(path,data):
            if path in emitted:return
            emitted.add(path);info=tarfile.TarInfo(path);info.size=len(data);info.mode=0o644;outgoing.addfile(info,io.BytesIO(data))
        def blob(data,media):
            digest=hashlib.sha256(data).hexdigest();put('blobs/sha256/'+digest,data)
            return {'mediaType':media,'digest':'sha256:'+digest,'size':len(data)}
        original=json.load(incoming.extractfile('manifest.json'))[0]
        config_bytes=incoming.extractfile(original['Config']).read()
        config=blob(config_bytes,'application/vnd.oci.image.config.v1+json')
        diff_ids=json.loads(config_bytes)['rootfs']['diff_ids']
        if len(diff_ids)!=len(original['Layers']):raise ValueError('Layer/config mismatch')
        layers=[]
        for path,expected in zip(original['Layers'],diff_ids):
            data=incoming.extractfile(path).read();compressed=data.startswith(b'\x1f\x8b')
            plain=gzip.decompress(data) if compressed else data
            if 'sha256:'+hashlib.sha256(plain).hexdigest()!=expected:raise ValueError('Layer content verification failed')
            layers.append(blob(data,'application/vnd.oci.image.layer.v1.tar'+('+gzip' if compressed else '')))
        manifest=blob(json.dumps({'schemaVersion':2,'mediaType':'application/vnd.oci.image.manifest.v1+json','config':config,'layers':layers}).encode(),'application/vnd.oci.image.manifest.v1+json')
        put('index.json',json.dumps({'schemaVersion':2,'manifests':[manifest]}).encode());put('oci-layout',b'{"imageLayoutVersion":"1.0.0"}')
