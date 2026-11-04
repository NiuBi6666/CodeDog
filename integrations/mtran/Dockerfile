FROM node:22-bookworm-slim
ENV NODE_ENV=production MT_ENABLE_UI=false MT_CHECK_UPDATE=false MT_LOG_REQUESTS=false MT_WORKER_IDLE_TIMEOUT=86400 MT_MODEL_DIR=/models MT_CONFIG_DIR=/config MT_HOST=0.0.0.0 MT_PORT=8989
RUN ONNXRUNTIME_NODE_INSTALL=skip npm install --global mtranserver@5.0.1 && npm cache clean --force
USER node
CMD ["mtranserver"]
