from flask import Flask, jsonify

app = Flask(__name__)


@app.route("/")
def index():
    return "Python recommendation service is running"


@app.route("/recommend/<int:user_id>")
def recommend(user_id):
    return jsonify({
        "user_id": user_id,
        "recommendations": [
            {
                "movie_id": 1,
                "title": "Toy Story",
                "score": 4.8
            },
            {
                "movie_id": 2,
                "title": "Jumanji",
                "score": 4.6
            }
        ]
    })


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=True)