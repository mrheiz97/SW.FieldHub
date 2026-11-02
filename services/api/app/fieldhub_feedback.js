"use strict";
(function(){
  const region=document.createElement("div");
  region.className="fh-toast-region";
  region.setAttribute("role","status");
  region.setAttribute("aria-live","polite");
  document.body.append(region);
  window.fhNotify=function(message,isError=false){
    const toast=document.createElement("div");
    toast.className="fh-toast"+(isError?" error":"");
    const label=document.createElement("span");label.textContent=String(message);
    const close=document.createElement("button");close.type="button";close.textContent="Schließen";
    close.setAttribute("aria-label","Meldung schließen");
    close.onclick=()=>toast.remove();
    toast.append(label,close);region.append(toast);
    // Keep messages until explicitly dismissed: no hidden timeouts.
  };
  // No global interception of requests or tokens.
  window.fhBusy=function(button,action){
    if(button.disabled)return Promise.resolve();
    button.disabled=true;button.setAttribute("aria-busy","true");
    return Promise.resolve().then(action).finally(()=>{button.disabled=false;button.removeAttribute("aria-busy")});
  };
})();
