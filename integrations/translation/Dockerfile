FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 TRANSLATORS_DEFAULT_REGION=CN
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --index-url https://mirrors.cloud.tencent.com/pypi/simple --timeout 20 --retries 2 -r requirements.txt && useradd --uid 1000 --create-home translator
COPY --from=node:22-bookworm-slim /usr/local/bin/node /usr/local/bin/node
RUN python -c "import exejs; assert exejs.evaluate('1+1') == 2"
COPY service.py test_service.py ./
RUN python -m unittest -v test_service
USER translator
EXPOSE 8090
CMD ["python", "service.py"]
