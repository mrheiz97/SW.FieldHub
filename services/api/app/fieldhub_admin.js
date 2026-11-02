"use strict";
// This script extends the local preview. Server-side authorization is authoritative.
const FH_PERMISSIONS=[
 ["forms.read","Formulare ansehen"],
 ["forms.submissions.read","Einreichungen ansehen"],
 ["forms.submissions.write","Einreichungen bearbeiten"],
 ["sync.write","Synchronisieren"],
 ["workspace.manage","Workspace verwalten"]
];
const fhRoot=()=>"/v1/workspaces/"+encodeURIComponent(workspace);
function fhButton(label,fn){const b=el("button",label);b.type="button";b.onclick=fn;return b}
function fhPanel(title){const p=el("section","","panel");p.append(el("h2",title));return p}
function fhCheckList(selected){const wrap=el("div","","fh-checks");for(const [value,label] of FH_PERMISSIONS){const row=el("label","","fh-check");const box=document.createElement("input");box.type="checkbox";box.value=value;box.checked=selected.includes(value);row.append(box,el("span",label));wrap.append(row)}return wrap}
function fhChecked(wrap){return [...wrap.querySelectorAll("input:checked")].map(x=>x.value)}
function fhField(label,value,max){const wrap=el("label","","fh-field");wrap.append(el("span",label));const input=document.createElement("input");input.value=value||"";input.maxLength=max;wrap.append(input);return {wrap,input}}
function fhDialog(title){const d=document.createElement("dialog");d.className="fh-dialog";const form=document.createElement("form");form.method="dialog";form.append(el("h2",title));d.append(form);document.body.append(d);d.addEventListener("close",()=>d.remove());return {d,form}}
function fhActions(form,save){const row=el("div","","fh-actions");const cancel=fhButton("Abbrechen",()=>form.closest("dialog").close());const submit=fhButton("Speichern",save);row.append(cancel,submit);form.append(row);return submit}
function fhAlert(message){$("message").textContent=message}
async function fhRoles(){const data=await api(fhRoot()+"/roles");return data.items||[]}
async function fhRenderRoles(){
 const content=$("content");content.replaceChildren();$("heading").textContent="Rollen";
 const header=el("div","","fh-heading");header.append(el("p","Individuelle Rollen und Berechtigungen verwalten.","small"),fhButton("Neue Rolle",()=>fhEditRole(null)));content.append(header);
 const panel=fhPanel("Rollen in diesem Workspace");content.append(panel);
 try{const roles=await fhRoles();if(!roles.length)panel.append(el("p","Noch keine individuellen Rollen vorhanden.","small"));
 for(const role of roles){const row=el("div","","fh-entry");const details=el("div");details.append(el("strong",role.name),el("p",role.description||role.permissions.join(", ")||"Keine Berechtigungen","small"));const actions=el("div","","fh-actions");actions.append(fhButton("Bearbeiten",()=>fhEditRole(role)),fhButton("Löschen",()=>fhDeleteRole(role)));row.append(details,actions);panel.append(row)}}catch(e){fhAlert(e.message)}
}
async function fhEditRole(role){
 const {d,form}=fhDialog(role?"Rolle bearbeiten":"Neue Rolle");
 const name=fhField("Rollenname",role?.name,100),description=fhField("Beschreibung",role?.description,500);
 const permissions=fhCheckList(role?.permissions||[]);form.append(name.wrap,description.wrap,el("p","Berechtigungen","small"),permissions);
 const save=async()=>{saveButton.disabled=true;try{const body={name:name.input.value.trim(),description:description.input.value.trim(),permissions:fhChecked(permissions)};if(!body.name)throw Error("Bitte einen Rollennamen eingeben.");await api(fhRoot()+"/roles"+(role?"/"+encodeURIComponent(role.id):""),role?"PUT":"POST",body);d.close();await fhRenderRoles()}catch(e){fhAlert(e.message);saveButton.disabled=false}};
 const saveButton=fhActions(form,save);d.showModal();name.input.focus();
}
async function fhDeleteRole(role){if(!confirm('Rolle "'+role.name+'" wirklich löschen?'))return;try{await fhRequestDelete(fhRoot()+"/roles/"+encodeURIComponent(role.id));await fhRenderRoles()}catch(e){fhAlert(e.message)}}
async function fhRequestDelete(path){const response=await fetch(path,{method:"DELETE",headers:{Authorization:"Bearer "+token},cache:"no-store"});if(!response.ok)throw Error("API "+response.status+": "+(await response.text()).slice(0,180))}
