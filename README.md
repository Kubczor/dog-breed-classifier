# 🐶 Dog Breed Classifier

<p align="center">
  <img src="./docs/demo.gif" width="400" alt="Demo GIF"/>
</p>

---

## 📖 Project Overview

This project is a dog breed classifier consisting of:

- **Backend API** built with FastAPI that hosts a trained TensorFlow model (`dogs-reco.h5`) and serves predictions.
- **Android app** that takes pictures and sends them to the backend API to recognize the dog breed.

---

## 🚀 Features

- Upload images via API or Android app
- Predicts dog breed using a deep learning model
- Easy to run locally or deploy

---

## ⚙️ Backend Setup

See the [backend README](backend/README.md) for detailed instructions on environment setup, dependencies, and running the server.

---

## ⚠️ Important

The model only recognizes dog breeds listed in `class_names.json`. Predictions outside this list may be inaccurate or unknown.

---

## 📱 Android App

The Android app is developed in Kotlin with Java backend integration. It communicates with the backend API to fetch predictions.

---


## 📫 Contact

If you have any questions or want to collaborate, feel free to reach me at: **kielinskij@gmail.com**

---

## 🤝 Contributions

Contributions are welcome! Feel free to open issues or pull requests.

---

*Made with ❤️ by Kuba*