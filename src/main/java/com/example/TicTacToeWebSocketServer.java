package com.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.InetSocketAddress;
import java.util.*;

public class TicTacToeWebSocketServer extends WebSocketServer {
    // change 1
//    private static final int PORT = 8080;
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "10000"));

    private final Gson gson = new Gson();

    // Game instance management
    // games: a map to store all active game instances (gameId, game instance)
    private final Map<String, GameInstance> games = new HashMap<>();
    // waitingQueue: a queue to store websocket connections of players waiting to be matched for a new game
    private final Queue<WebSocket> waitingQueue = new LinkedList<>();
    // playerToGameId: a map which tracks which game each player(websocket) belongs to
    private final Map<WebSocket, String> playerToGameId = new HashMap<>();
    // gameCounter: count of games (used to generate unique game ids)
    private int gameCounter = 0;

    public TicTacToeWebSocketServer() {
//        super(new InetSocketAddress(PORT));
        // change 2
        super(new InetSocketAddress("0.0.0.0", PORT));
    }

    private class GameInstance {
        private final String gameId;
        private final String[] board = new String[9];
        private String currentTurn = "X";
        private String winner = null;
        private boolean gameStarted = false;
        private boolean gameEnded = false;
        private WebSocket playerX = null;
        private WebSocket playerO = null;

        private boolean waitingForResponses = false;
        private Boolean playerXWantsToPlay = null;
        private Boolean playerOWantsToPlay = null;

        public GameInstance(String gameId) {
            this.gameId = gameId;
            initializeBoard();
        }

        private void initializeBoard() {
            for (int i = 0; i < 9; i++) {
                board[i] = String.valueOf(i + 1);
            }
        }

        public void assignPlayers(WebSocket x, WebSocket o) {
            this.playerX = x;
            this.playerO = o;
            playerToGameId.put(x, gameId);
            playerToGameId.put(o, gameId);

            sendMessage(x, createMessage("playerAssigned", "X", "You are Player X in Game #" + gameId));
            sendMessage(o, createMessage("playerAssigned", "O", "You are Player O in Game #" + gameId));

            gameStarted = true;
            broadcastGameState();

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    broadcastToGame(createMessage("gameStart", currentTurn, "Game #" + gameId + " started! " + currentTurn + "'s turn"));
                }
            }, 50);
        }

        public boolean handleMove(WebSocket conn, int position) {
            if (!gameStarted || gameEnded) {
                sendMessage(conn, createMessage("error", null, "Game not active"));
                return false;
            }

            String playerSymbol = null;
            if (conn == playerX) {
                playerSymbol = "X";
            } else if (conn == playerO) {
                playerSymbol = "O";
            }

            if (playerSymbol == null || !playerSymbol.equals(currentTurn)) {
                sendMessage(conn, createMessage("error", null, "Not your turn"));
                return false;
            }

            if (position < 1 || position > 9) {
                sendMessage(conn, createMessage("error", null, "Invalid position"));
                return false;
            }

            if (board[position - 1].equals("X") || board[position - 1].equals("O")) {
                sendMessage(conn, createMessage("error", null, "Position already taken"));
                return false;
            }

            // Make the move
            board[position - 1] = currentTurn;
            winner = checkWinner();

            if (winner != null) {
                gameEnded = true;
                broadcastGameState();

                if ("draw".equals(winner)) {
                    broadcastToGame(createMessage("gameEnd", "draw", "Game #" + gameId + " ended - It's a draw!"));
                } else {
                    broadcastToGame(createMessage("gameEnd", winner, "Game #" + gameId + " ended - " + winner + " wins!"));
                }

                askPlayersToPlayAgain();
                return true;
            } else {
                currentTurn = currentTurn.equals("X") ? "O" : "X";
                broadcastGameState();
                broadcastToGame(createMessage("turnChange", currentTurn, currentTurn + "'s turn"));
            }

            return false;
        }

        public void reset() {
            initializeBoard();
            currentTurn = "X";
            winner = null;
            gameEnded = false;
            waitingForResponses = false;
            playerXWantsToPlay = null;
            playerOWantsToPlay = null;
            broadcastToGame(createMessage("gameReset", null, "Game #" + gameId + " has been reset"));
            broadcastGameState();
            if (gameStarted) {
                broadcastToGame(createMessage("gameStart", currentTurn, currentTurn + "'s turn"));
            }
        }

        private void askPlayersToPlayAgain() {
            waitingForResponses = true;
            playerXWantsToPlay = null;
            playerOWantsToPlay = null;

            if (playerX != null) {
                sendMessage(playerX, createMessage("askPlayAgain", null,
                        "Game ended! Do you want to play another game? (Type 'yes' or 'no')"));
            }
            if (playerO != null) {
                sendMessage(playerO, createMessage("askPlayAgain", null,
                        "Game ended! Do you want to play another game? (Type 'yes' or 'no')"));
            }

            System.out.println("Asked players in game #" + gameId + " if they want to play again");
        }

        public void handlePlayAgainResponse(WebSocket conn, boolean wantsToPlay) {
            if (!waitingForResponses) {
                sendMessage(conn, createMessage("error", null, "No response needed at this time"));
                return;
            }

            if (conn == playerX) {
                playerXWantsToPlay = wantsToPlay;
                sendMessage(conn, createMessage("responseReceived", null,
                        wantsToPlay ? "You chose to play again!" : "You chose to stop playing."));
            } else if (conn == playerO) {
                playerOWantsToPlay = wantsToPlay;
                sendMessage(conn, createMessage("responseReceived", null,
                        wantsToPlay ? "You chose to play again!" : "You chose to stop playing."));
            }

            checkAllResponsesReceived();
        }

        private void checkAllResponsesReceived() {
            boolean hasAllResponses =
                    (playerX == null || playerXWantsToPlay != null) &&
                            (playerO == null || playerOWantsToPlay != null);

            if (!hasAllResponses) {
                return;
            }

            // if we have all responses:
            waitingForResponses = false;

            // Check if both players want to continue playing together
            boolean bothWantToContinue =
                    (playerX != null && playerXWantsToPlay==Boolean.TRUE) &&
                            (playerO != null && playerOWantsToPlay==Boolean.TRUE);

            if (bothWantToContinue) {
                // Reset the game for the same players to continue
                reset();
                System.out.println("Same players continuing in game #" + gameId);
                return;
            }

            // Handle players who don't want to play
            if (playerX != null && playerXWantsToPlay==Boolean.FALSE) {
                sendMessage(playerX, createMessage("leftGame", null, "Thanks for playing! You can reconnect anytime."));
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            playerX.close();
                        } catch (Exception e) {
                        }
                    }
                }, 500);
                removePlayer(playerX);
            }

            if (playerO != null && playerOWantsToPlay==Boolean.FALSE) {
                sendMessage(playerO, createMessage("leftGame", null, "Thanks for playing! You can reconnect anytime."));
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            playerO.close();
                        } catch (Exception e) {
                        }
                    }
                }, 500);
                removePlayer(playerO);
            }

            // Add players who want to continue (but their partner doesn't) back to queue
            if (playerX != null && playerXWantsToPlay==Boolean.TRUE) {
                TicTacToeWebSocketServer.this.waitingQueue.offer(playerX);
                sendMessage(playerX, createMessage("backToQueue", null, "You're back in queue for a new game!"));
                removePlayer(playerX);
            }

            if (playerO != null && playerOWantsToPlay==Boolean.TRUE) {
                TicTacToeWebSocketServer.this.waitingQueue.offer(playerO);
                sendMessage(playerO, createMessage("backToQueue", null, "You're back in queue for a new game!"));
                removePlayer(playerO);
            }
        }

        private String checkWinner() {
            // horizontal
            if ((board[0] + board[1] + board[2]).equals("XXX")) {
                return "X";
            } else if ((board[0] + board[1] + board[2]).equals("OOO")) {
                return "O";
            } else if ((board[3] + board[4] + board[5]).equals("OOO")) {
                return "O";
            } else if ((board[3] + board[4] + board[5]).equals("XXX")) {
                return "X";
            } else if ((board[6] + board[7] + board[8]).equals("OOO")) {
                return "O";
            } else if ((board[6] + board[7] + board[8]).equals("XXX")) {
                return "X";
            }
            // vertical
            if ((board[0] + board[3] + board[6]).equals("XXX")) {
                return "X";
            } else if ((board[0] + board[3] + board[6]).equals("OOO")) {
                return "O";
            } else if ((board[1] + board[4] + board[7]).equals("OOO")) {
                return "O";
            } else if ((board[1] + board[4] + board[7]).equals("XXX")) {
                return "X";
            } else if ((board[2] + board[5] + board[8]).equals("OOO")) {
                return "O";
            } else if ((board[2] + board[5] + board[8]).equals("XXX")) {
                return "X";
            }
            // diagonal
            else if ((board[0] + board[4] + board[8]).equals("OOO")) {
                return "O";
            } else if ((board[0] + board[4] + board[8]).equals("XXX")) {
                return "X";
            } else if ((board[2] + board[4] + board[6]).equals("OOO")) {
                return "O";
            } else if ((board[2] + board[4] + board[6]).equals("XXX")) {
                return "X";
            }

            for (int i = 0; i < 9; i++) {
                if (board[i].equals(String.valueOf(i + 1))) {
                    return null; // game not done
                }
            }
            return "draw"; // game done but no winner
        }

        private void broadcastToGame(JsonObject message) {
            String messageStr = gson.toJson(message);
            if (playerX != null && playerX.isOpen()) {
                playerX.send(messageStr);
            }
            if (playerO != null && playerO.isOpen()) {
                playerO.send(messageStr);
            }
        }

        private void broadcastGameState() {
            JsonObject json = new JsonObject();
            json.addProperty("type", "gameState");
            json.add("board", gson.toJsonTree(board));
            json.addProperty("currentTurn", currentTurn);
            json.addProperty("gameStarted", gameStarted);
            json.addProperty("gameEnded", gameEnded);
            json.addProperty("winner", winner);
            json.addProperty("gameId", gameId);
            json.addProperty("queueSize", waitingQueue.size());
            broadcastToGame(json);
        }

        public void removePlayer(WebSocket conn) {
            if (conn == playerX) {
                playerX = null;
            } else if (conn == playerO) {
                playerO = null;
            }
            playerToGameId.remove(conn);
        }

        public boolean isEmpty() {
            return playerX == null && playerO == null;
        }

        public boolean hasPlayer(WebSocket conn) {
            return conn == playerX || conn == playerO;
        }
    }

    private void handlePlayAgainResponse(WebSocket conn, boolean wantsToPlay) {
        String gameId = playerToGameId.get(conn);
        if (gameId == null) {
            sendMessage(conn, createMessage("error", null, "You are not in an active game"));
            return;
        }

        GameInstance game = games.get(gameId);
        if (game != null) {
            game.handlePlayAgainResponse(conn, wantsToPlay);

            if (game.isEmpty()) {
                games.remove(gameId);
                System.out.println("Removed completed game #" + gameId);
                createGamesFromQueue();
            }
        }
    }

    // called when a new WebSocket connection is established
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection: " + conn.getRemoteSocketAddress());
        waitingQueue.add(conn);
        createGamesFromQueue();
    }

    // called when a WebSocket connection is closed
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Connection closed: " + conn.getRemoteSocketAddress());

        waitingQueue.remove(conn);

        String gameId = playerToGameId.get(conn);
        if (gameId != null) {
            GameInstance game = games.get(gameId);
            if (game != null) {
                game.removePlayer(conn);

                if (game.playerX != null) {
                    sendMessage(game.playerX, createMessage("playerDisconnected", null, "Other player disconnected from Game #" + gameId));
                }
                if (game.playerO != null) {
                    sendMessage(game.playerO, createMessage("playerDisconnected", null, "Other player disconnected from Game #" + gameId));
                }

                if (game.isEmpty()) {
                    games.remove(gameId);
                    System.out.println("Removed empty game #" + gameId);
                }
            }
        }

        playerToGameId.remove(conn);
    }

    // called when a message arrives from the client
    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message: " + message);
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String action = json.get("action").getAsString();

            if ("makeMove".equals(action)) {
                handleMove(conn, json.get("position").getAsInt());
            } else if ("resetGame".equals(action)) {
                handleReset(conn);
            } else if ("playAgain".equals(action)) {
                handlePlayAgainResponse(conn, json.get("response").getAsBoolean());
            }
        } catch (Exception e) {
            System.out.println("Error parsing message: " + e.getMessage());
            sendMessage(conn, createMessage("error", null, "Invalid message format"));
        }
    }

    // called when an error occurs
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("WebSocket error: " + ex.getMessage());
    }

    // called when the WebSocket server is started
    @Override
    public void onStart() {
        System.out.println("Server started successfully!");
    }

    private void createGamesFromQueue() {
        while (waitingQueue.size() >= 2) {
            WebSocket playerX = waitingQueue.poll();
            WebSocket playerO = waitingQueue.poll();

            String gameId = String.valueOf(++gameCounter);
            GameInstance newGame = new GameInstance(gameId);
            games.put(gameId, newGame);

            newGame.assignPlayers(playerX, playerO);

            System.out.println("Created new game #" + gameId + " with " +
                    playerX.getRemoteSocketAddress() + " (X) and " +
                    playerO.getRemoteSocketAddress() + " (O)");
        }
    }

    private void handleMove(WebSocket conn, int position) {
        String gameId = playerToGameId.get(conn);
        if (gameId == null) {
            sendMessage(conn, createMessage("error", null, "You are not in an active game"));
            return;
        }

        GameInstance game = games.get(gameId);
        if (game == null) {
            sendMessage(conn, createMessage("error", null, "Game not found"));
            return;
        }

        boolean gameEnded = game.handleMove(conn, position);

        if (!gameEnded) {
            createGamesFromQueue();
        }
    }

    private void handleReset(WebSocket conn) {
        String gameId = playerToGameId.get(conn);
        if (gameId == null) {
            sendMessage(conn, createMessage("error", null, "You are not in an active game"));
            return;
        }

        GameInstance game = games.get(gameId);
        if (game != null) {
            game.reset();
        }
    }

    private JsonObject createMessage(String type, String data, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        if (data != null) json.addProperty("data", data);
        if (message != null) json.addProperty("message", message);
        return json;
    }

    private void sendMessage(WebSocket conn, JsonObject message) {
        if (conn != null && conn.isOpen()) {
            conn.send(gson.toJson(message));
        }
    }

    public static void main(String[] args) {
        TicTacToeWebSocketServer server = new TicTacToeWebSocketServer();
        server.start();
        System.out.println("Server started on port " + PORT);
    }
}
