import {describe,it,expect} from 'vitest';
import {syncMessage,syncExpired} from './sync-state';
describe('sync progress',()=>{
 it('distinguishes dispatch, crawl, ingestion and translation',()=>{
  expect(syncMessage({phase:'queued'})).toContain('等待调度');
  expect(syncMessage({phase:'crawling'})).toContain('等待排队和抓取');
  expect(syncMessage({phase:'ingesting',queued_batches:3,pending_articles:4})).toContain('3 个批次');
  expect(syncMessage({phase:'translating',processing_articles:1,pending_articles:5},2)).toContain('已新增 2 篇');
  expect(syncMessage({phase:'translating',processing_articles:1,pending_articles:5})).toContain('另有 5 篇排队');
 });
 it('exposes safe terminal messages',()=>{
  expect(syncMessage({phase:'uncertain',message:'调度结果未确认'})).toBe('调度结果未确认');
  expect(syncMessage({phase:'failed',message:'调度被拒绝'})).toBe('调度被拒绝');
  expect(syncMessage({phase:'idle'},3)).toContain('同步完成');
 });
});

it('monitoring stops at ten minutes without cancelling the backend task',()=>{expect(syncExpired(1000,600999)).toBe(false);expect(syncExpired(1000,601000)).toBe(true);});
