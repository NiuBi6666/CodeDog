<script setup>
import { computed, useId } from "vue";
import { Crown, Gem, Star, Sparkles, ChevronsUp } from "@lucide/vue";
const props=defineProps({level:{type:Number,default:1},name:{type:String,default:"青铜"},stars:{type:Number,default:1}});
const id=useId().replace(/:/g,'')+'-metal';
const icon=computed(()=>props.level===7?Crown:props.level===6?Sparkles:props.level>=4?Gem:props.level===3?Star:ChevronsUp);
const colors=['#c58d68','#d3dfef','#ffd26e','#83e7e3','#8ac6ff','#c5a3ff','#ffdb87'];
const metal=computed(()=>colors[Math.max(0,Math.min(6,props.level-1))]);
</script>
<template><span class="rank-emblem" :class="`emblem-${level}`" :style="{'--metal':metal}" :aria-label="`${name} ${stars} 星`"><span class="emblem-shield"><svg viewBox="0 0 44 48" width="38" height="42" aria-hidden="true"><defs><linearGradient :id="id" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff4d9"/><stop offset=".42" :stop-color="metal"/><stop offset="1" stop-color="#274561"/></linearGradient></defs><path v-if="level>=4" d="M8 9 2 15 7 31 13 35M36 9l6 6-5 16-6 4" :fill="`url(#${id})`" opacity=".6"/><path d="M22 2 38 10 35 31 22 45 9 31 6 10Z" :fill="`url(#${id})`"/><path d="M22 6 34 12 31 29 22 39 13 29 10 12Z" fill="#13263f"/><path d="M12 13 22 9 32 13" fill="none" :stroke="metal" opacity=".6"/><path d="m18 35 4 5 4-5" fill="none" :stroke="metal"/></svg><component :is="icon" class="emblem-core" :size="17" aria-hidden="true"/></span><span class="emblem-label">{{ name }}<small aria-hidden="true"><span v-for="n in 5" :key="n" :class="{lit:n<=stars}">★</span></small></span></span></template>
<style scoped>
.rank-emblem{display:inline-flex;align-items:center;gap:6px;color:var(--metal);font-size:11px;font-weight:700;line-height:1.15;white-space:nowrap}.emblem-shield{position:relative;display:grid;place-items:center;width:38px;height:42px;flex-shrink:0}.emblem-core{position:absolute;top:12px;left:10px}.emblem-label small{display:flex;gap:1px;margin-top:5px;font-size:8px;color:#45516b;letter-spacing:0}.emblem-label .lit{color:var(--metal)}.emblem-7 .emblem-shield,.emblem-6 .emblem-core{animation:emblem-breathe 4s ease-in-out infinite}@keyframes emblem-breathe{50%{filter:drop-shadow(0 0 5px var(--metal))}}@media(prefers-reduced-motion:reduce){.rank-emblem *{animation:none!important}}
</style>
