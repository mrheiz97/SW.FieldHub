"use strict";
(function(){
async function show(){
 const area=byId("content");area.replaceChildren();byId("page-title").textContent="Recruiting";
 const panel=element("section","","panel"),heading=element("h2","Kontakte"),search=document.createElement("input"),add=element("button","Kontakt hinzufügen"),list=element("div");
 search.placeholder="Kontakte suchen";panel.append(heading,search,add,list);area.append(panel);
 let rows=[];
 function draw(){list.replaceChildren();for(const item of rows.filter(x=>x.name.toLowerCase().includes(search.value.toLowerCase()))){const row=element("div","","entry");row.append(element("strong",item.name),element("span",item.status));if(item.followUpAt)row.append(element("span",new Date(item.followUpAt).toLocaleString("de-DE")));const edit=element("button","Bearbeiten");edit.onclick=()=>editContact(item);row.append(edit);list.append(row)}if(!list.childElementCount)list.append(element("p","Keine Kontakte vorhanden."))}
 async function reload(){const data=await request(root()+"/recruiting/contacts");rows=data.items||[];draw()}
 function editContact(item){const dialog=document.createElement("dialog");dialog.className="modal";const form=document.createElement("form");form.method="dialog";form.append(element("h2",item?"Kontakt bearbeiten":"Neuer Kontakt"));
 function field(label,value){const wrap=element("label","","field"),input=document.createElement("input");input.value=value||"";wrap.append(element("span",label),input);form.append(wrap);return input}
 const name=field("Name",item?.name),notes=field("Notizen",item?.notes),follow=field("Wiedervorlage",item?.followUpAt?.slice(0,16));follow.type="datetime-local";const statusSelect=document.createElement("select");for(const s of ["new","contacted","interested","converted","declined"]){const o=element("option",s);o.value=s;statusSelect.append(o)}statusSelect.value=item?.status||"new";form.append(statusSelect);
 const cancel=element("button","Abbrechen"),save=element("button","Speichern");cancel.type="button";cancel.onclick=()=>dialog.close();save.type="button";save.onclick=async()=>{save.disabled=true;try{await request(root()+"/recruiting/contacts"+(item?"/"+encodeURIComponent(item.id):""),item?"PUT":"POST",{name:name.value,notes:notes.value,status:statusSelect.value,followUpAt:follow.value?new Date(follow.value).toISOString():null});dialog.close();await reload()}catch(e){status(e.message);save.disabled=false}};form.append(cancel,save);dialog.append(form);document.body.append(dialog);dialog.onclose=()=>dialog.remove();dialog.showModal()}
 add.onclick=()=>editContact(null);search.oninput=draw;try{await reload()}catch(e){status(e.message)}
}
window.fhRecruiting={show};
})();