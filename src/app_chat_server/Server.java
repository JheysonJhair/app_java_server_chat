package app_chat_server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Server {

    // Map para almacenar los escritores y socket de cada cliente por nombre de usuario
    private static Map<String, PrintWriter> clientWriters = new HashMap<>();
    private static Map<String, Socket> clientSockets = new HashMap<>();
    // Map para almacenar el buffer y nombre de archivos por clave
    private static Map<String, byte[]> fileBuffer = new HashMap<>();
    private static Map<String, String> fileNames = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("El servidor está corriendo...");
        ServerSocket listener = new ServerSocket(12346);
        try {
            while (true) {
                // Acepta nuevas conexiones y crea un nuevo hilo para manejarlas
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Inicializa los lectores y escritores para el socket del cliente
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                // Lee el nombre de usuario enviado por el cliente
                name = in.readLine();
                synchronized (clientWriters) {
                    // Verifica si el nombre de usuario ya está en uso
                    if (clientWriters.containsKey(name)) {
                        out.println("ERROR Nombre de usuario ya en uso");
                        return;
                    }
                    // Almacena el escritor y el socket del cliente
                    clientWriters.put(name, out);
                    clientSockets.put(name, socket);
                }

                System.out.println("SERVER: Cliente conectado: " + name);

                // Notifica a todos los clientes la lista actualizada de usuarios
                broadcastUserList();
                
                String message;
                while ((message = in.readLine()) != null) {
                    // Maneja los mensajes recibidos del cliente
                    if (message.startsWith("FILE:")) {
                        System.out.println("SERVER: Mensaje de archivo recibido");
                        handleFileMessage(message);
                    } else if (message.startsWith("UPDATE_FILE_LIST:")) {
                        handleUpdateFileList(message);
                    } else {
                        System.out.println("SERVER: Mensaje recibido de " + name + ": " + message);
                        handleMessage(message);
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                // Elimina al cliente al desconectarse
                if (name != null) {
                    clientWriters.remove(name);
                    clientSockets.remove(name);
                    System.out.println("SERVER: Cliente desconectado: " + name);
                    broadcastUserList();
                }
                try {
                    socket.close();
                } catch (Exception e) {
                }
            }
        }

        // Maneja mensajes de texto normales
        private void handleMessage(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String[] userParts = parts[0].split("@");
                if (userParts.length == 2) {
                    String receiver = userParts[1].trim();
                    synchronized (clientWriters) {
                        PrintWriter writer = clientWriters.get(receiver);
                        if (writer != null) {
                            writer.println(message);
                            System.out.println("SERVER: Mensaje enviado de " + name + " a " + receiver + ": " + message);
                        } else {
                            System.out.println("SERVER: El receptor " + receiver + " no está conectado.");
                        }
                    }
                }
            }
        }

        // Maneja mensajes que contienen archivos
        private void handleFileMessage(String message) {
            String[] parts = message.split(":", 4);
            if (parts.length == 4) {
                String senderReceiver = parts[1];
                String fileName = parts[2];
                byte[] fileContent = Base64.getDecoder().decode(parts[3]);

                if (fileContent == null || fileContent.length == 0) {
                    System.out.println("SERVER: El contenido del archivo recibido es nulo o vacío.");
                } else {
                    String key = senderReceiver + ":" + fileName;
                    fileBuffer.put(key, fileContent);
                    fileNames.put(key, fileName);

                    System.out.println("SERVER: Archivo almacenado en el servidor: " + fileName + ", Key: " + key + ", Tamaño: " + fileContent.length);

                    // Notifica al receptor que un archivo está disponible
                    String[] users = senderReceiver.split("@");
                    if (users.length == 2) {
                        String receiver = users[1].trim();
                        synchronized (clientWriters) {
                            PrintWriter writer = clientWriters.get(receiver);
                            if (writer != null) {
                                writer.println("FILE:" + senderReceiver + ":" + fileName + ":" + Base64.getEncoder().encodeToString(fileContent));
                                System.out.println("SERVER: Notificación de archivo enviado a " + receiver + ": " + key);
                            }
                        }
                    }
                }
            }
        }

        // Maneja la actualización de la lista de archivos disponibles
        private void handleUpdateFileList(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String[] userParts = parts[1].split("@");
                if (userParts.length == 2) {
                    String requester = userParts[0].trim();
                    String sender = userParts[1].trim();
                    PrintWriter writer = clientWriters.get(requester);
                    if (writer != null) {
                        for (Map.Entry<String, String> entry : fileNames.entrySet()) {
                            String key = entry.getKey();
                            if (key.startsWith(sender + "@")) {
                                writer.println("FILE_AVAILABLE:" + key);
                                System.out.println("SERVER: Lista de archivos actualizada para " + requester + ": " + key);
                            }
                        }
                    }
                }
            }
        }

        // Notifica a todos los clientes la lista actualizada de usuarios
        private void broadcastUserList() {
            StringBuilder userListMessage = new StringBuilder("USER_LIST");
            synchronized (clientWriters) {
                Set<String> userNames = clientWriters.keySet();
                for (String userName : userNames) {
                    userListMessage.append(" ").append(userName);
                }
                for (PrintWriter writer : clientWriters.values()) {
                    writer.println(userListMessage.toString());
                }
                System.out.println("SERVER: Lista de usuarios actualizada: " + userListMessage.toString());
            }
        }
    }
}
