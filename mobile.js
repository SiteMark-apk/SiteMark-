'use strict';
let installationPrompt;
const installArea=document.createElement('div');installArea.className='install-help';
const networkStatus=document.createElement('div');networkStatus.className='network-status';networkStatus.setAttribute('role','status');
document.querySelector('aside').insertBefore(networkStatus,document.querySelector('.local'));
document.querySelector('aside').insertBefore(installArea,networkStatus);
function installed(){return matchMedia('(display-mode: standalone)').matches;}
function installUI(){
  if(installed()){installArea.hidden=true;return;}
  installArea.hidden=false;
  installArea.innerHTML=installationPrompt?'<button id="install-site">Install SiteMark</button>':'On Android: open Chrome menu ⋮ and select Install app or Add to Home screen.';
  const button=document.getElementById('install-site');if(button)button.onclick=async()=>{const prompt=installationPrompt;installationPrompt=null;await prompt.prompt();await prompt.userChoice;installUI()};
}
window.addEventListener('beforeinstallprompt',e=>{e.preventDefault();installationPrompt=e;installUI()});
window.addEventListener('appinstalled',()=>{installationPrompt=null;installArea.hidden=true});
function connectivity(){networkStatus.textContent=navigator.onLine?'Online · local storage':'Offline · local storage';}window.addEventListener('online',connectivity);window.addEventListener('offline',connectivity);connectivity();installUI();
function addCamera(){const drop=document.getElementById('drop');if(!drop||drop.querySelector('.site-camera'))return;const input=document.createElement('input');input.type='file';input.accept='image/*';input.setAttribute('capture','environment');input.hidden=true;const button=document.createElement('button');button.className='site-camera primary';button.textContent='Take photo';button.type='button';button.onclick=()=>input.click();input.onchange=()=>{if(input.files.length&&current)upload([...input.files],current);input.value=''};drop.append(button,input);}
new MutationObserver(addCamera).observe(document.getElementById('main'),{childList:true});addCamera();
if('serviceWorker' in navigator&&window.isSecureContext){navigator.serviceWorker.register('./sw.js').catch(error=>console.warn('Could not enable offline access',error));}
