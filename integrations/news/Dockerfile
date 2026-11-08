FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --index-url https://mirrors.cloud.tencent.com/pypi/simple --timeout 20 --retries 2 -r requirements.txt && useradd --uid 1000 --create-home collector
COPY crawler.py gateway.py test_crawler.py repair_images.py ./
RUN python -m unittest -v test_crawler
USER collector
EXPOSE 8091
CMD ["python","gateway.py"]
