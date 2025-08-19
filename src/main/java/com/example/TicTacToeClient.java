package com.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.net.URI;
import java.util.Scanner;

public class TicTacToeClient extends WebSocketClient {
    private final Gson gson = new Gson();
    private String playerSymbol = null;
    private String[] board = new String[9];
    private String currentTurn = "X";
    private boolean gameStarted = false;
    private boolean gameEnded = false;
    private String winner = null;
    private boolean inQueue = false;
    private String currentGameId = null;
    private boolean waitingForPlayAgainResponse = false;
    private Scanner scanner = new Scanner(System.in);
    private boolean shouldQuit = false;

    public TicTacToeClient(URI serverURI) {
        super(serverURI);
        initializeBoard();
    }

    private void initializeBoard() {
        for (int i = 0; i < 9; i++) {
            board[i] = String.valueOf(i + 1);
        }
    }

    // when web socket connection is successful
    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to server");
    }

    // when we receive a message from the server
    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            handleMessage(json);
        } catch (Exception e) {
            System.out.println("Error parsing message: " + e.getMessage());
        }
    }

    // when the web socket connection is closed
    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Connection closed " + reason);
        System.exit(0); // terminates the application
    }

    // when the web socket encounters an error
    @Override
    public void onError(Exception e) {
        System.out.println("Connection error: " + e.getMessage());
    }

    // to process different types of messages from the server
    private void handleMessage(JsonObject message) {
        String type = message.get("type").getAsString();

        switch (type) {
                // to handle assignment of symbol: either X or O
            case "playerAssigned":
                // gets the assigned symbol from the msg
                playerSymbol = message.get("data").getAsString();
                inQueue = false;
                System.out.println("You are Player " + playerSymbol);
                break;

                // to handle queue status update
            case "queueUpdate":
                if (!inQueue) {
                    inQueue = true;
                    playerSymbol = null;
                }
                System.out.println(message.get("message").getAsString());
                break;

                // to handle game state updates and turn updates
            case "gameState":
                // updates client side variables
                updateGameState(message);
                displayBoard();
                if (gameStarted && !gameEnded && !inQueue && playerSymbol != null) {
                    checkTurn();
                }
                break;

                // to tell users when the game starts
            case "gameStart":
                System.out.println("\nGame started");
                break;

                // to handle game completion
            case "gameEnd":
                // update variables, then display final board
                updateGameState(message);
                displayBoard();
                System.out.println("\n"+message.get("message").getAsString());
                break;

                // to handle the servers play again prompt
            case "askPlayAgain":
                waitingForPlayAgainResponse = true;
                System.out.println("\nDo you want to play another game? (yes/no)");
                break;

                // to confirm server received play-again response
            case "responseReceived":
                waitingForPlayAgainResponse = false;
                System.out.println(message.get("message").getAsString());
                if (message.get("message").getAsString().contains("stop playing")) {
                    shouldQuit = true;
                } else {
                    System.out.println("Waiting for other player's response...");
                }
                break;

                // to handle server msg that the player has left the game
            case "leftGame":
                inQueue = false;
                playerSymbol = null;
                currentGameId = null;
                waitingForPlayAgainResponse = false;
                shouldQuit = true;
                System.out.println(message.get("message").getAsString());
                try {
                    close();
                } catch (Exception e) {
                }
                System.exit(0);
                break;

                // handles when the player should return to queue for matchmaking
            case "backToQueue":
                inQueue = true;
                playerSymbol = null;
                currentGameId = null;
                waitingForPlayAgainResponse = false;
                System.out.println("Back in queue for new game");
                break;

                // to notify the user when the game is reset
            case "gameReset":
                System.out.println("Game reset");
                break;

                // to notify when the opponent disconnects
            case "playerDisconnected":
                System.out.println(message.get("message").getAsString());
                break;

                // to display error msg from server
            case "error":
                System.out.println(message.get("message").getAsString());
                break;
        }
    }

    // to update client side variables based on msg from server
    private void updateGameState(JsonObject message) {
        // to update board
        if (message.has("board") && !message.get("board").isJsonNull()) {
            JsonArray boardArray = message.getAsJsonArray("board");
            for (int i = 0; i < 9; i++) {
                board[i] = boardArray.get(i).getAsString();
            }
        }

        // to update current turn
        if (message.has("currentTurn") && !message.get("currentTurn").isJsonNull()) {
            currentTurn = message.get("currentTurn").getAsString();
        }

        // to update whether the game has started
        if (message.has("gameStarted") && !message.get("gameStarted").isJsonNull()) {
            gameStarted = message.get("gameStarted").getAsBoolean();
        }

        // to update whether the game has ended
        if (message.has("gameEnded") && !message.get("gameEnded").isJsonNull()) {
            gameEnded = message.get("gameEnded").getAsBoolean();
        }

        // to update the winner
        if (message.has("winner") && !message.get("winner").isJsonNull()) {
            winner = message.get("winner").getAsString();
        }

        // to update current gameId
        if (message.has("gameId") && !message.get("gameId").isJsonNull()) {
            currentGameId = message.get("gameId").getAsString();
        }
    }

    private void displayBoard() {
        System.out.println("\nCurrent Board (Game #" + currentGameId + "):");

        System.out.println(" " + board[0] + " | " + board[1] + " | " + board[2]);
        System.out.println("---|---|---");
        System.out.println(" " + board[3] + " | " + board[4] + " | " + board[5]);
        System.out.println("---|---|---");
        System.out.println(" " + board[6] + " | " + board[7] + " | " + board[8]);
        System.out.println();
    }

    private void checkTurn() {
        if (currentTurn.equals(playerSymbol)) {
            System.out.println("It's your turn! Enter position (1-9):");
        } else {
            String otherPlayer = currentTurn.equals("X") ? "X" : "O";
            System.out.println("Waiting for Player " + otherPlayer + "...");
        }
    }

    // to send move command to server
    private void sendMove(int position) {
        JsonObject message = new JsonObject();
        message.addProperty("action", "makeMove");
        message.addProperty("position", position);
        // converts json object to json string
        send(gson.toJson(message));
    }

    // to tell server to reset game
    private void sendResetGame() {
        JsonObject message = new JsonObject();
        message.addProperty("action", "resetGame");
        send(gson.toJson(message));
    }

    // to respond to servers play again prompt
    private void sendContinueResponse(boolean wantsToContinue) {
        JsonObject message = new JsonObject();
        message.addProperty("action", "playAgain");
        message.addProperty("response", wantsToContinue);
        send(gson.toJson(message));
    }

    // main user interaction loop
    public void startGameLoop() {
        System.out.println("Tic Tac Toe Client");
        System.out.println("Commands: 1-9 (move), 'reset' (reset game), 'yes'/'no' (after game), 'quit' (exit)");
        System.out.println();

        // while connected and not quitting
        while (isOpen() && !shouldQuit) {
            try {
                String input = scanner.nextLine().trim();

                if ("quit".equalsIgnoreCase(input)) {
                    close();
                    break;
                } else if ("yes".equalsIgnoreCase(input)) {
                    if (waitingForPlayAgainResponse) {
                        sendContinueResponse(true);
                    } else {
                        System.out.println("No question pending.");
                    }
                } else if ("no".equalsIgnoreCase(input)) {
                    if (waitingForPlayAgainResponse) {
                        sendContinueResponse(false);
                        shouldQuit = true;
                        System.out.println("Exiting game...");
                        close();
                        break; // exit while loop
                    } else {
                        System.out.println("No question pending.");
                    }
                } else if ("reset".equalsIgnoreCase(input)) {
                    if (!inQueue && playerSymbol != null) {
                        sendResetGame();
                    } else {
                        System.out.println("Cannot reset - not in active game.");
                    }
                } else if (input.matches("[1-9]")) {
                    int position = Integer.parseInt(input);
                    if (gameStarted && !gameEnded && !inQueue && playerSymbol != null && currentTurn.equals(playerSymbol)) {
                        sendMove(position);
                    } else if (gameEnded) {
                        System.out.println("Game ended. Wait for new game");
                    } else if (inQueue) {
                        System.out.println("You're in queue. Please wait");
                    } else if (playerSymbol == null) {
                        System.out.println("Not in game. Please wait");
                    } else if (!currentTurn.equals(playerSymbol)) {
                        System.out.println("Not your turn.");
                    } else {
                        System.out.println("Game hasn't started yet.");
                    }
                }
                // if input is present
                else if (!input.isEmpty()) {
                    if (waitingForPlayAgainResponse) {
                        System.out.println("Please answer 'yes' or 'no'.");
                    } else {
                        System.out.println("Invalid input. Use 1-9, 'reset', 'yes'/'no', or 'quit'.");
                    }
                }
            } catch (Exception e) {
                System.out.println("Error reading input. Try again.");
            }
        }
    }

    public static void main(String[] args) {
        try {
            URI serverURI = new URI("ws://localhost:8080");
            // creates client object
            TicTacToeClient client = new TicTacToeClient(serverURI);

            System.out.println("Connecting to server...");
            // initiates actual web socket connection to server
            client.connect();

            Thread.sleep(1000);

            if (client.isOpen()) {
                client.startGameLoop();
            } else {
                System.out.println("Failed to connect. Make sure server is running on localhost:8080");
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}