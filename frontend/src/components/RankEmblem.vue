<script setup>
import { computed } from "vue";
import { Shield, Gem, Star, ChevronsUp } from "@lucide/vue";
const props=defineProps({level:{type:Number,default:1},name:{type:String,default:"石墨"}});
const icon=computed(()=>props.level>=5?Gem:props.level>=3?Star:ChevronsUp);
</script>
<template><span class="rank-emblem" :class="`emblem-${level}`" :aria-label="`${name}，等级 ${level}`"><span class="emblem-shield"><Shield :size="30" aria-hidden="true"/><component :is="icon" class="emblem-core" :size="14" aria-hidden="true"/></span><span>{{ name }}<small>LV.{{ level }}</small></span></span></template>
<style scoped>
.rank-emblem{--metal:#acbbd0;display:inline-flex;align-items:center;gap:7px;color:var(--metal);font-size:11px;font-weight:600;line-height:1.2;white-space:nowrap}.emblem-2{--metal:#e3ac7a}.emblem-3{--metal:#e0edff}.emblem-4{--metal:#ffdb70}.emblem-5{--metal:#71b7ff}.emblem-6{--metal:#9bf5ed}.emblem-shield{position:relative;display:grid;place-items:center;width:32px;height:34px;filter:drop-shadow(0 0 5px color-mix(in srgb,var(--metal) 25%,transparent))}.emblem-shield>svg:first-child{fill:color-mix(in srgb,var(--metal) 12%,#101e34);stroke-width:1.5}.emblem-core{position:absolute;top:8px;left:9px}.rank-emblem small{display:block;margin-top:3px;font:9px Consolas,monospace;letter-spacing:.08em;opacity:.7}.emblem-6 .emblem-core{animation:emblem-light 3s ease-in-out infinite}@keyframes emblem-light{50%{filter:drop-shadow(0 0 5px #9bf5ed)}}@media(prefers-reduced-motion:reduce){.emblem-6 .emblem-core{animation:none}}
</style>
