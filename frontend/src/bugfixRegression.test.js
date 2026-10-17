import { readFileSync } from "node:fs";
import { computed, reactive, ref } from "vue";
import { describe, expect, it, vi } from "vitest";
import { buildAuditQuery } from "./auditUi";

function logs() {
  const code=readFileSync(new URL("./views/StructuredLogListView.vue",import.meta.url),"utf8")
    .split("<script setup>")[1].split("</script>")[0].replace(/^import .*;$/gm,"");
  const route={query:{},fullPath:"/logs"};
  const router={resolve:({query})=>({fullPath:"/logs"+(Object.keys(query).length?"?"+new URLSearchParams(query):"")}),push:vi.fn()};
  const api=vi.fn().mockResolvedValue({logs:[],total:0});
  const fetch=vi.fn().mockResolvedValue({ok:false,json:async()=>({error:"QA export interception"})});
  const notify=vi.fn();
  const state=new Function("computed","reactive","ref","watch","onMounted","useRoute","useRouter","api","notify","hasPermission","buildAuditQuery","fetch",code+";return {filters,load,search,reset,openDetail,closeDetail,detail,detailBusy,exportCsv};")
    (computed,reactive,ref,vi.fn(),vi.fn(),()=>route,()=>router,api,notify,()=>true,buildAuditQuery,fetch);
  return {...state,api,route,router,fetch};
}
describe("audit refresh and cancellation regressions",()=>{
  it("requests on every identical search and reset, and clears unsent edits",async()=>{
    const s=logs();await s.search();await s.search();await s.reset();await s.reset();
    expect(s.api).toHaveBeenCalledTimes(4);
    s.filters.keyword="unsent";await s.reset();expect(s.filters.keyword).toBe("");expect(s.api).toHaveBeenCalledTimes(5);
  });
  it("navigates changed filters once and lets the route watcher load",async()=>{
    const s=logs();s.filters.keyword="submitted";await s.search();
    expect(s.router.push).toHaveBeenCalledOnce();expect(s.api).not.toHaveBeenCalled();
  });
  it("exports applied filters rather than unsubmitted input",async()=>{
    const s=logs();s.route.query={module:"rankings"};s.filters.keyword="unsubmitted";
    await s.exportCsv();expect(s.fetch.mock.calls[0][0]).toContain("module=rankings");
    expect(s.fetch.mock.calls[0][0]).not.toContain("unsubmitted");
  });
  it("closes during loading and discards a late response",async()=>{
    const s=logs();let resolve;s.api.mockImplementationOnce(()=>new Promise(r=>resolve=r));
    const pending=s.openDetail(1);expect(s.detailBusy.value).toBe(true);s.closeDetail();
    expect(s.detailBusy.value).toBe(false);resolve({id:1});await pending;expect(s.detail.value).toBeNull();
  });
  it("does not replace a newer detail with an older response",async()=>{
    const s=logs();let resolve;s.api.mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockResolvedValueOnce({id:2});
    const old=s.openDetail(1);await s.openDetail(2);resolve({id:1});await old;expect(s.detail.value.id).toBe(2);
  });
  it("retains the latest list response when requests finish out of order",async()=>{
    const s=logs();let resolve;s.api.mockImplementationOnce(()=>new Promise(r=>resolve=r)).mockResolvedValueOnce({logs:[],total:2});
    const old=s.load();await s.load();resolve({logs:[],total:1});await old;expect(s.api).toHaveBeenCalledTimes(2);
  });
});

describe("student logout failure",()=>{
  function logout(api) {
    const view=readFileSync(new URL("./views/PublicRankingView.vue",import.meta.url),"utf8");
    const code=view.slice(view.indexOf("async function logout()"),view.indexOf("function validateLogin("));
    const handleSessionExpired=vi.fn(),showAlert=vi.fn();
    return {run:new Function("api","showAlert","handleSessionExpired",code+";return logout;")(api,showAlert,handleSessionExpired),handleSessionExpired,showAlert};
  }
  it("keeps session visible and gives retry guidance when logout fails",async()=>{
    const s=logout(vi.fn().mockRejectedValue({status:503,message:"服务不可用"}));await s.run();
    expect(s.handleSessionExpired).not.toHaveBeenCalled();expect(s.showAlert).toHaveBeenCalledWith(expect.stringContaining("请重试"));
  });
  it("clears the page only after logout succeeds or the server confirms session expiry",async()=>{
    for(const api of [vi.fn().mockResolvedValue({}),vi.fn().mockRejectedValue({status:401})]){
      const s=logout(api);await s.run();expect(s.handleSessionExpired).toHaveBeenCalledOnce();
    }
  });
});
