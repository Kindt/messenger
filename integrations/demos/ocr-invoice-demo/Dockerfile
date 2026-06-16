FROM python:3.12-slim
WORKDIR /app
COPY _lib /app/demos/_lib
COPY ocr-invoice-demo /app/demos/ocr-invoice-demo
WORKDIR /app/demos/ocr-invoice-demo
ENV OCR_WORKER_PORT=8095
EXPOSE 8095
CMD ["python", "app.py"]
