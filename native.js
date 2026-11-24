'use strict';
// This script is packaged only in the Android application, not GitHub Pages.
if(window.SiteMarkNative && typeof window.SiteMarkNative.postMessage==='function'){
 const bridge=window.SiteMarkNative,oldNativeCamera=cameraView,oldNativeTile=photoTile,oldNativeDetails=photoDetails;
 bridge.onmessage=event=>{try{const result=JSON.parse(event.data);if(result.message)notify(result.message)}catch{}};
 saveToPhone=function(photo){if(!photo?.image)return;try{bridge.postMessage(JSON.stringify({type:'savePhoto',id:uid(),image:photo.image}))}catch{notify('Could not save phone copy')}};
 download=function(photo){saveToPhone(photo)};
 window.print=function(){bridge.postMessage(JSON.stringify({type:'print'}))};
 photoTile=function(p){return oldNativeTile(p).replace('Save to phone','Save to Gallery')};
 photoDetails=function(id){oldNativeDetails(id);const button=dialog.querySelector('[data-action="download"]');if(button)button.textContent='Save to Gallery'};
 cameraView=function(){oldNativeCamera();const status=main.querySelector('.phone-copy-status small');if(status)status.textContent='New photos are saved directly to the SiteMark album in your Android Gallery.';const copy=main.querySelector('.save-phone-toggle');if(copy&&copy.lastChild)copy.lastChild.textContent=' Save to Android Gallery automatically';const btn=main.querySelector('.last-photo-save');if(btn){btn.textContent='Save last photo to Gallery';btn.onclick=()=>saveToPhone(photos[0])}};
 if(ready&&screenMode==='camera')cameraView();
}else{
 const oldNativeCamera=cameraView;cameraView=function(){oldNativeCamera();const copy=main.querySelector('.phone-copy-status small');if(copy)copy.textContent='Update Android System WebView to enable native Gallery saving.'};
 if(ready&&screenMode==='camera')cameraView();
}
