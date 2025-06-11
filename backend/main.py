import os
from fastapi import FastAPI, UploadFile, File, Response
from fastapi.middleware.cors import CORSMiddleware
import tensorflow as tf
import numpy as np
from PIL import Image
from io import BytesIO
import json

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

model = tf.keras.models.load_model(
    "dogs-reco.h5",
    compile=False,
    custom_objects={
        'InputLayer': tf.keras.layers.InputLayer,
        'GlobalAveragePooling2D': tf.keras.layers.GlobalAveragePooling2D
    }
)

with open("class_names.json", "r") as f:
    class_names = json.load(f)

@app.post("/predict")
async def predict(file: UploadFile = File(...)):

    image = Image.open(BytesIO(await file.read())).resize((331, 331)).convert("RGB")
    img_array = np.expand_dims(np.array(image) / 255.0, axis=0)

    preds = model.predict(img_array)
    predicted_class = class_names[np.argmax(preds)]

    return Response(content=predicted_class, media_type="text/plain")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port="YOUR-PORT")