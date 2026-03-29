FROM python:3.12-slim

WORKDIR /app
RUN python -m venv /app/venv

# Keep the container alive indefinitely waiting for docker exec commands
CMD ["tail", "-f", "/dev/null"]