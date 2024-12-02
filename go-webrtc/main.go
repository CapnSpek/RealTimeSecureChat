/*
This Go program serves as the local WebRTC server on the client machine, which interacts with the central Java server
for encrypted communication. The local Go server handles all signaling and WebRTC peer connection management for the
Java client (referred to as Client A or Client B).

1. **TCP Socket Server**:
   - The Go server listens for incoming client connections on port `9000`. Only one client connection is allowed at a time..

2. **Client Communication**:
   - When a client connects, it sends a message to the Go server through a TCP socket.
   - The Go server processes the messages, including commands like `getInfo` (used to request WebRTC connection details).

3. **WebRTC Setup**:
   - Upon receiving a `getInfo` message, the Go server initializes a WebRTC `PeerConnection` to handle the media connection between two clients (Client A and Client B).
   - The server creates an SDP (Session Description Protocol) offer and gathers ICE (Interactive Connectivity Establishment) candidates.
   - The server sends the SDP offer and ICE candidates back to the requesting Java client (Client A or Client B).

4. **PeerConnection and ICE Candidates**:
   - The `PeerConnection` is used to establish a peer-to-peer connection between the clients, with the ICE candidates being used for NAT traversal.
   - The connection info (SDP and ICE candidates) is serialized into a message and sent back to the client.

5. **Message Handling**:
   - The Go server supports handling different types of messages, like `getInfo` (for connection info), and responds accordingly.
   - Error messages are also sent if any part of the process (e.g., creating the PeerConnection, setting the SDP, etc.) fails.

6. **Thread Safety**:
   - A mutex (`clientMutex`) is used to ensure thread-safe access to the single client connection (`clientConn`) in the event of multiple simultaneous connections.
   - Only one client can be connected at a time, and attempts to connect while another client is already connected are rejected.

The goal of this Go server is to facilitate the creation of a secure WebRTC connection between Java clients via WebSocket and peer-to-peer WebRTC signaling.

*/

/*
Message structure:
{
    "type": "string",    // Type of message, e.g., "getInfo", "error", "connectionInfo"
    "from": "string",    // Sender ID (Java Client)
    "to": "string",      // Receiver ID (target peer)
    "payload": "string"  // Data payload (e.g., SDP, ICE candidates, error message)
}
*/

package main

import (
	"encoding/json"
	"log"
	"net"
	"sync"

	"github.com/pion/webrtc/v3"
)

var (
	peerConnections = make(map[string]*webrtc.PeerConnection) // Tracks active PeerConnections
	clientMutex     sync.Mutex                                // Mutex for thread-safe access
	clientConn      net.Conn                                  // Singleton client connection
)

// Message structure for communication
type Message struct {
	Type    string `json:"type"`    // Command type (connect, getInfo, etc.)
	From    string `json:"from"`    // Sender ID (Java Client)
	To      string `json:"to"`      // Target peer ID
	Payload string `json:"payload"` // Payload (SDP, ICE, etc.)
}

func main() {
	// Start the local WebRTC server
	startSocketServer()
}

// Start the TCP server for client communication
func startSocketServer() {
	listener, err := net.Listen("tcp", ":9000")
	if err != nil {
		log.Fatalf("Failed to start socket server: %v", err)
	}
	defer listener.Close()

	log.Println("Local WebRTC Go server running on :9000")

	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Printf("Error accepting connection: %v", err)
			continue
		}

		clientMutex.Lock()
		if clientConn != nil {
			log.Println("Client already connected. Rejecting new connection.")
			conn.Close()
			clientMutex.Unlock()
			continue
		}

		clientConn = conn
		clientMutex.Unlock()

		log.Println("Java Client connected:", conn.RemoteAddr())
		go handleClient(conn)
	}
}

/*
handleClient JSON structure (messages from Java Client):
{
    "type": "string",    // Type of command from the client (e.g., "getInfo")
    "from": "string",    // Sender ID (Java Client)
    "to": "string",      // Target peer ID
    "payload": "string"  // Data associated with the command (e.g., empty for "getInfo")
}
*/

// Handle communication with the Java Client
func handleClient(conn net.Conn) {
	defer func() {
		clientMutex.Lock()
		clientConn = nil
		clientMutex.Unlock()
		conn.Close()
		log.Println("Java Client disconnected.")
	}()

	for {
		buffer := make([]byte, 1024)
		n, err := conn.Read(buffer)
		if err != nil {
			log.Printf("Error reading from client: %v", err)
			break
		}

		var msg Message
		err = json.Unmarshal(buffer[:n], &msg)
		if err != nil {
			log.Printf("Invalid message format: %v", err)
			continue
		}

		processMessage(msg, conn)
	}
}

/*
processMessage JSON structure (example for "getInfo"):
{
    "type": "getInfo",  // The type of command requested by the Java Client
    "to": "string",     // Target ID (may be empty)
    "payload": ""       // Typically empty for "getInfo", can contain other relevant data
}
*/

// Process incoming client messages
func processMessage(msg Message, conn net.Conn) {
	switch msg.Type {
	case "getInfo":
		// Provide connection details for the Java Client
		log.Printf("Received 'getInfo' command from Java Client")
		provideConnectionInfo(msg.From)
	case "connect":
		// Handle connection requests between peers
		log.Printf("Received 'connect' command from Java Client")
		connectPeers(msg)
	default:
		log.Printf("Unknown command type: %s", msg.Type)
		sendError(conn, "Unknown command type")
	}
}

func connectPeers(msg Message) {
	// Parse connection details from the message payload
	var connectionDetails struct {
		SDP           string   `json:"sdp"`
		ICECandidates []string `json:"iceCandidates"`
	}

	err := json.Unmarshal([]byte(msg.Payload), &connectionDetails)
	if err != nil {
		log.Printf("Failed to parse connection details: %v", err)
		sendError(clientConn, "Invalid connection details")
		return
	}

	// Extract the user from the Message object directly
	user := msg.To // Assuming the `To` field contains the target user's ID
	if user == "" {
		log.Printf("Missing user field in message")
		sendError(clientConn, "Missing user field in message")
		return
	}

	// Retrieve or create a PeerConnection for the user
	clientMutex.Lock()
	peerConnection, exists := peerConnections[user]
	if !exists {
		// Create a new PeerConnection if one does not already exist
		config := webrtc.Configuration{
			ICEServers: []webrtc.ICEServer{
				{URLs: []string{"stun:stun.l.google.com:19302"}},
			},
		}
		peerConnection, err = webrtc.NewPeerConnection(config)
		if err != nil {
			log.Printf("Error creating PeerConnection: %v", err)
			sendError(clientConn, "Failed to create PeerConnection")
			clientMutex.Unlock()
			return
		}

		// Track ICE candidates
		peerConnection.OnICECandidate(func(candidate *webrtc.ICECandidate) {
			if candidate != nil {
				log.Printf("Generated ICE candidate for %s: %v", user, candidate.ToJSON().Candidate)
			}
		})

		peerConnections[user] = peerConnection
	}
	clientMutex.Unlock()

	// Set the remote SDP description
	err = peerConnection.SetRemoteDescription(webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  connectionDetails.SDP,
	})
	if err != nil {
		log.Printf("Failed to set remote description for %s: %v", user, err)
		sendError(clientConn, "Failed to set remote description")
		return
	}

	// Add ICE candidates
	for _, candidate := range connectionDetails.ICECandidates {
		err = peerConnection.AddICECandidate(webrtc.ICECandidateInit{
			Candidate: candidate,
		})
		if err != nil {
			log.Printf("Failed to add ICE candidate for %s: %v", user, err)
		}
	}

	// Create or retrieve a DataChannel
	dataChannel, err := peerConnection.CreateDataChannel("dataChannel", nil)
	if err != nil {
		log.Printf("Failed to create data channel for %s: %v", user, err)
		sendError(clientConn, "Failed to create data channel")
		return
	}

	// Setup DataChannel event handlers
	dataChannel.OnOpen(func() {
		log.Printf("DataChannel opened for %s", user)
	})
	dataChannel.OnMessage(func(msg webrtc.DataChannelMessage) {
		log.Printf("Received message on DataChannel from %s: %s", user, string(msg.Data))
	})

	log.Printf("Connection established with %s", user)
}

/*
provideConnectionInfo JSON structure (response sent to the Java Client):
{
    "sdp": "string",             // SDP (Session Description Protocol) offer in string format
    "iceCandidates": [           // List of ICE candidates in string format
        "candidate1",
        "candidate2",
        ...
    ]
}
Example:
{
    "sdp": "v=0...",
    "iceCandidates": [
        "candidate1",
        "candidate2"
    ]
}
*/

// Provide connection info (SDP and ICE candidates) to the Java Client
func provideConnectionInfo(clientID string) {
	clientMutex.Lock()
	defer clientMutex.Unlock()

	// Create a new WebRTC PeerConnection
	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{"stun:stun.l.google.com:19302"}}, // STUN server for NAT traversal
		},
	}

	peerConnection, err := webrtc.NewPeerConnection(config)
	if err != nil {
		log.Printf("Error creating PeerConnection: %v", err)
		sendClientMessage("error", clientID, "", "Failed to create PeerConnection")
		return
	}

	// Track ICE candidates as they're generated
	var iceCandidates []string
	peerConnection.OnICECandidate(func(candidate *webrtc.ICECandidate) {
		if candidate != nil {
			iceCandidates = append(iceCandidates, candidate.ToJSON().Candidate)
		}
	})

	// Create an SDP offer
	offer, err := peerConnection.CreateOffer(nil)
	if err != nil {
		log.Printf("Error creating SDP offer: %v", err)
		sendClientMessage("error", clientID, "", "Failed to create SDP offer")
		return
	}

	// Set the local description
	err = peerConnection.SetLocalDescription(offer)
	if err != nil {
		log.Printf("Error setting local description: %v", err)
		sendClientMessage("error", clientID, "", "Failed to set local description")
		return
	}

	// Store the PeerConnection in the map
	peerConnections[clientID] = peerConnection

	// Wait for ICE gathering to complete
	peerConnection.OnICEGatheringStateChange(func(state webrtc.ICEGathererState) {
		if state == webrtc.ICEGathererStateComplete {
			// Prepare the response payload with SDP and ICE candidates
			log.Printf("SDP: %s", offer.SDP)
			log.Printf("Final ICE Candidates: %v", iceCandidates)

			payload := map[string]interface{}{
				"sdp":           offer.SDP,
				"iceCandidates": iceCandidates,
			}

			// Send the connection info to the Java Client
			payloadData, err := json.Marshal(payload)
			if err != nil {
				log.Printf("Error marshaling JSON: %v", err)
				return
			}

			log.Printf("Connection info: %s", string(payloadData))
			sendClientMessage("connectionInfo", clientID, "", string(payloadData))
			log.Printf("Connection info sent to Java Client %s", clientID)
		}
	})
}

/*
sendClientMessage JSON structure:
{
    "type": "string",    // Type of the message (e.g., "connectionInfo", "error")
    "from": "string",    // Sender ID (e.g., server, client ID)
    "to": "string",      // Recipient ID (target peer ID or Java Client)
    "payload": "string"  // The data to be sent (SDP, ICE candidates, or error message)
}
Example:
{
    "type": "connectionInfo",
    "from": "server",
    "to": "clientID",
    "payload": "{\"sdp\": \"v=0...\", \"iceCandidates\": [\"candidate1\", \"candidate2\"]}"
}
*/

// Send a message back to the Java Client
func sendClientMessage(msgType, to, from, payload string) {
	if clientConn == nil {
		log.Println("No client connection available")
		return
	}

	response := Message{
		Type:    msgType,
		From:    from,
		To:      to,
		Payload: payload,
	}

	data, err := json.Marshal(response)
	if err != nil {
		log.Printf("Error marshaling message: %v", err)
		return
	}

	// Add newline to the data
	data = append(data, '\n')

	_, err = clientConn.Write(data)
	if err != nil {
		log.Printf("Error sending message to client: %v", err)
	}
	log.Printf("Sent message to Java Client %s", data)
}

/*
sendError JSON structure:
{
    "type": "error",    // Type of message (always "error" for errors)
    "payload": "string" // Error message detailing what went wrong
}
Example:
{
    "type": "error",
    "payload": "Failed to create PeerConnection"
}
*/

// Send an error response to the client
func sendError(conn net.Conn, errorMsg string) {
	response := Message{
		Type:    "error",
		Payload: errorMsg,
	}

	data, err := json.Marshal(response)
	if err != nil {
		log.Printf("Error marshaling error response: %v", err)
		return
	}

	_, err = conn.Write(data)
	if err != nil {
		log.Printf("Error sending error response: %v", err)
	}
}
