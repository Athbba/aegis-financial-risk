#!/usr/bin/env python3
"""Bounded operator check; exit nonzero for stale/missing backup, low disk or API error ratio."""
import argparse,datetime as dt,json,pathlib,shutil,sys
p=argparse.ArgumentParser();p.add_argument('--backup-status',type=pathlib.Path,required=True);p.add_argument('--disk',type=pathlib.Path,required=True);p.add_argument('--errors',type=int,required=True);p.add_argument('--requests',type=int,required=True);a=p.parse_args()
issues=[]
try:
 d=json.loads(a.backup_status.read_text());age=(dt.datetime.now(dt.timezone.utc)-dt.datetime.fromisoformat(d['createdAt'].replace('Z','+00:00'))).total_seconds()
 if age<0 or age>86400 or not d.get('remoteVerified'):issues.append('backup missing remote verification or older than RPO 24h')
except (OSError,ValueError,KeyError):issues.append('backup evidence missing/invalid')
usage=shutil.disk_usage(a.disk)
if usage.free/usage.total<0.15:issues.append('disk free below 15%')
if a.requests<0 or a.errors<0 or a.errors>a.requests or (a.requests>=100 and a.errors/a.requests>=0.01):issues.append('API error ratio >=1% or invalid observations')
print(json.dumps({'status':'ALERT' if issues else 'PASS','issues':issues}));sys.exit(bool(issues))
