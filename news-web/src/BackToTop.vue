<script setup>
import { ref,watch,onMounted,onBeforeUnmount,nextTick } from 'vue';
const props=defineProps({target:{default:null},viewport:Boolean,resetKey:{default:''},label:{type:String,default:'回到顶部'}});
const visible=ref(false),right=ref(20);
let bound;
function update(){
 const target=props.target;
 visible.value=props.viewport?window.scrollY>window.innerHeight:!!target&&target.scrollTop>target.clientHeight;
 right.value=props.viewport?20:Math.max(16,window.innerWidth-(target?.getBoundingClientRect().right||window.innerWidth)+16);
}
function bind(){
 bound?.removeEventListener('scroll',update);
 bound=props.viewport?window:props.target;
 bound?.addEventListener('scroll',update,{passive:true});
 update();
}
function top(){
 (props.viewport?window:props.target)?.scrollTo({top:0,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});
}
watch(()=>props.target,bind,{flush:'post'});
watch(()=>props.resetKey,async()=>{await nextTick();update();});
onMounted(()=>{bind();window.addEventListener('resize',update);});
onBeforeUnmount(()=>{bound?.removeEventListener('scroll',update);window.removeEventListener('resize',update);});
</script>
<template>
 <button v-if="visible" type="button" class="back-top" :class="viewport?'back-top--viewport':'back-top--panel'" :style="{right:right+'px'}" :aria-label="label" :title="label" @click="top">
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 4h14M12 20V8m-6 6 6-6 6 6"/></svg>
 </button>
</template>
