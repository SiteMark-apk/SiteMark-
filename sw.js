'use strict';
const CACHE='sitemark-shell-v12';
const assets=['./index.html','./mobile.css','./mobile.js','./field.css','./field.js','./location.js','./places.js','./places.css','./launch.js','./launch.css','./simple.js','./simple.css','./manifest.webmanifest','./icon-camera-192.png','./icon-camera-512.png'];
self.addEventListener('install',event=>{event.waitUntil((async()=>{const cache=await caches.open(CACHE);for(const path of assets){const response=await fetch(new Request(path,{cache:'reload'}));if(!response.ok||response.redirected)throw new Error('Offline asset unavailable: '+path);await cache.put(path,response);}await self.skipWaiting()})())});
self.addEventListener('activate',event=>{event.waitUntil((async()=>{for(const key of await caches.keys())if(key.startsWith('sitemark-shell-')&&key!==CACHE)await caches.delete(key);await self.clients.claim()})())});
self.addEventListener('fetch',event=>{const request=event.request,url=new URL(request.url);if(request.method!=='GET'||url.origin!==self.location.origin)return;
if(request.mode==='navigate'){event.respondWith((async()=>{try{return await fetch(request)}catch(error){return (await caches.match('./index.html'))||Response.error()}})());return;}
if(assets.some(path=>new URL(path,self.location.href).href===url.href)){event.respondWith((async()=>{const cached=await caches.match(request);return cached||fetch(request)})());}});
