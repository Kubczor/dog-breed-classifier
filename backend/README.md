# 🐶 Dog Breed Classifier API (FastAPI Backend)

This is a FastAPI backend for classifying dog breeds using a trained deep learning model. It receives images via an API and returns the predicted breed.

---

## ✅ System Requirements

- **OS:** Debian 10/11
- **Python:** 3.11.11
- **RAM:** Minimum 2 GB (recommended 4 GB)
- **CPU:** 2 cores (recommended 4 cores)
- **Disk space:** At least 1 GB

---

## 📦 Required System Packages

Before running the backend, make sure to install Python and pip:


sudo apt update
sudo apt install -y python3 python3-pip python3-venv

---

## ⚙️ Setup & Installation
1. Create a virtual environment and install dependencies:

```bash
python3 -m venv venv
source venv/bin/activate


pip install --upgrade pip
pip install -r requirements.txt
```

3. Run the server:


```bash
uvicorn main:app --host 0.0.0.0 --port YOUR_PORT
```
Replace YOUR_PORT with your desired port number (e.g., 8000).


