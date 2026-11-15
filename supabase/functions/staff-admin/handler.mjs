// Native fetch only; no SDK dependency or client-controlled admin URL/key.
export function createHandler({url, publicKey, serviceKey, fetcher=fetch}) {
  const reply=(status,code,data={})=>new Response(JSON.stringify({code,...data}),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  const passwordOK=p=>typeof p==='string'&&p.length>=12&&p.length<=72&&new TextEncoder().encode(p).length<=72;
  return async request=>{
    if(request.method!=='POST')return reply(405,'METHOD_NOT_ALLOWED');
    if(!url||!publicKey||!serviceKey)return reply(503,'NOT_CONFIGURED');
    const authorization=request.headers.get('Authorization');
    if(!authorization?.startsWith('Bearer '))return reply(401,'AUTH_REQUIRED');
    try {
      // Verify the token with Auth; never decode an unverified caller/user/role.
      const call=async(path,body,admin=false,method='POST')=>{
        const response=await fetcher(url+path,{method,redirect:'error',signal:AbortSignal.timeout(12000),headers:{apikey:admin?serviceKey:publicKey,Authorization:admin?`Bearer ${serviceKey}`:authorization,'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
        const json=await response.json().catch(()=>({}));
        return {status:response.status,ok:response.ok,json};
      };
      const caller=await call('/auth/v1/user',undefined,false,'GET');
      if(!caller.ok||!caller.json.id)return reply(401,'AUTH_REQUIRED');
      // Bound the stream even when Content-Length is absent or dishonest.
      let bytes=0;const chunks=[];const reader=request.body?.getReader();
      if(!reader)return reply(422,'INVALID');
      while(true){const {done,value}=await reader.read();if(done)break;bytes+=value.length;if(bytes>8192){await reader.cancel();return reply(413,'TOO_LARGE');}chunks.push(value);}
      const bodyBytes=new Uint8Array(bytes);let offset=0;for(const c of chunks){bodyBytes.set(c,offset);offset+=c.length;}
      let body;try{body=JSON.parse(new TextDecoder().decode(bodyBytes));}catch{return reply(422,'INVALID');}
      if(!body||typeof body!=='object'||Array.isArray(body))return reply(422,'INVALID');
      const rpc=(name,args,admin=false)=>call('/rest/v1/rpc/'+name,args,admin);
      const failure=result=>reply([401,403,409,413,422,423,429].includes(result.status)?result.status:503,'REQUEST_FAILED');
      if(body.action==='change_password') {
        const {current_password:old,password}=body;
        if(!passwordOK(password)||typeof old!=='string'||old.length>128||password===old)return reply(422,'INVALID');
        const gate=await rpc('staff_password_change_required',{});
        if(!gate.ok)return failure(gate);
        if(gate.json!==true)return reply(409,'PASSWORD_CHANGE_NOT_REQUIRED');
        // Reauthenticate using the current password; JWT alone cannot clear the gate.
        const check=await call('/auth/v1/token?grant_type=password',{email:caller.json.email,password:old});
        if(!check.ok||check.json.user?.id!==caller.json.id)return reply(422,'INVALID_CURRENT_PASSWORD');
        // Do not leave the additional reauthentication session active.
        if(check.json.access_token)await fetcher(url+'/auth/v1/logout?scope=local',{method:'POST',headers:{apikey:publicKey,Authorization:`Bearer ${check.json.access_token}`},signal:AbortSignal.timeout(12000)});
        const updated=await call('/auth/v1/admin/users/'+caller.json.id,{password},true,'PUT');
        if(!updated.ok)return reply(422,'PASSWORD_REJECTED');
        const completed=await rpc('complete_staff_password_change',{p_user_id:caller.json.id},true);
        return completed.ok?reply(200,'PASSWORD_CHANGED'):failure(completed);
      }
      if(body.action!=='create'||!passwordOK(body.password))return reply(422,'INVALID');
      const args={p_device_id:body.device_id,p_device_secret:body.device_secret,p_operation_id:body.operation_id,p_email:body.email,p_full_name:body.full_name,p_role:body.role};
      const reserved=await rpc('prepare_staff_account',args);
      if(!reserved.ok)return failure(reserved);
      const job=reserved.json;
      if(!job.completed) {
        const owned=user=>user?.id===job.user_id&&user.email?.toLowerCase()===job.email&&user.app_metadata?.pos_provision_operation===job.operation_id;
        let existing=await call('/auth/v1/admin/users/'+job.user_id,undefined,true,'GET');
        if(existing.status===404) {
          const created=await call('/auth/v1/admin/users',{id:job.user_id,email:job.email,password:body.password,email_confirm:true,app_metadata:{pos_provision_operation:job.operation_id}},true);
          if(created.ok)existing=created;
          else {
            // A racing/lost-response create may already own this exact identity.
            existing=await call('/auth/v1/admin/users/'+job.user_id,undefined,true,'GET');
            if(!existing.ok) {
              if(existing.status===404&&[400,422].includes(created.status)&&created.json.code!=='email_exists'&&created.json.error_code!=='email_exists')return reply(422,'PASSWORD_REJECTED');
              return reply(created.status>=500?503:409,'AUTH_CREATION_UNRESOLVED');
            }
          }
        }
        if(!existing.ok)return failure(existing);
        if(!owned(existing.json))return reply(409,'IDENTITY_CONFLICT');
      }
      // Live role/proof is checked again after Auth IO; creation and audit commit once.
      const finished=await rpc('finish_staff_account',{p_device_id:body.device_id,p_device_secret:body.device_secret,p_operation_id:body.operation_id});
      return finished.ok?reply(200,'CREATED',finished.json):failure(finished);
    } catch {return reply(503,'UNAVAILABLE');} // Never log bodies, tokens, email, passwords or Auth error text.
  };
}
