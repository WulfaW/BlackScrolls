# BlackScrolls 🎴🖤

Welcome to **BlackScrolls**, a fully animated, interactive Blackjack game built with **Java Swing**. Immerse yourself in a medieval casino experience, playing against intelligent AI opponents and a dealer. Featuring animations, sound effects, and multilingual support, BlackScrolls brings the thrill of the card table to your screen.

---

## Table of Contents
- [Features](#features)
- [How to Run](#how-to-run)
- [How to Play](#how-to-play)
- [Controls](#controls)
- [License](#license)

---

## Features
- **Dynamic Gameplay**: Fully animated card dealing and turn-based action. 🃏✨
- **Intelligent AI**: Compete against AI opponents on both sides of the table. 🤖
- **Advanced Player Actions**: Supports standard moves plus splitting hands.
- **Interactive UI**: Intuitive buttons for **Hit, Stand, Split, Double Down,** and **New Round**.
- **Betting System**: Place your bets and watch your balance grow against the AI. 💰
- **Multilingual Support**: Switch between English and Turkish on the fly. 🌐
- **Immersive Audio**: Background music and sound effects for a complete experience. 🔊🎵

---

## How to Run

### The Easy Way (Recommended)
The easiest way to play is to download the pre-built game.

1.  Go to the [**Releases**](https://github.com/WulfaW/BlackScrolls/releases) page of this repository.
2.  Download the `BlackScrolls.jar` file.
3.  Run the game with Java:
    ```bash
    java -jar BlackScrolls.jar
    ```

### For Developers (Building from Source)
If you want to build the project yourself:

1.  **Prerequisites**: Make sure you have Java Development Kit (JDK) 8 or higher.
2.  **Clone the repository**: `git clone https://github.com/WulfaW/BlackScrolls.git`
3.  **Assets**: Ensure the `assets` and `cards` folders are in the project's root directory. The game needs these files to work:
    - `/assets/`: `background.jpg`, `speaker.png`, `speaker-muted.png`, and sound files (`.wav`).
    - `/cards/`: Card images in `VALUE-SUIT.png` format (e.g., `A-HEART.png`) and `BACK.png`.
4.  **Compile and Run**:
    ```bash
    # Navigate to the project directory
    cd blackjack
    # Compile all Java files
    javac *.java
    # Run the main application
    java App
    ```

---

## How to Play
1. When the game starts, place your bet.
2. Cards are dealt. Choose your action:
   - **Hit**: Draw another card.
   - **Stand**: End your turn.
   - **Split**: If you have a pair, split it into two separate hands.
   - **Double Down**: Double your bet, draw one final card, and end your turn.
3. The AI and dealer will play their turns automatically.
4. Results are shown, and you can start a new round.

---

## Controls

The in-game buttons allow you to perform the following actions:

| Button | Action |
| :--- | :--- |
| **Hit** | Draw a card for the active hand. |
| **Stand** | End the turn for the active hand. |
| **Split** | Split a pair into two separate hands. |
| **Double Down** | Double your bet and draw one final card. |
| **New Round** | Start a new round after the current one ends. |
| **?** | Show the tutorial. |
| **TR / EN** | Switch the game's language. |
| **🔊 / 🔇** | Toggle the background music. |

---

## License

This project is licensed under the MIT License.

Copyright (c) 2025 Wulfa W.

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.