package main

import (
	"encoding/json"
	"log"
	"net"
	"net/http"
	"sync"

	"github.com/pion/webrtc/v3"
)

var (
	// Map to track active WebRTC PeerConnections by a unique peer ID (e.g., username)
	peerConnections = make(map[string]*webrtc.PeerConnection)

	// Mutex to ensure thread-safe access to the peerConnections map
	mutex sync.Mutex

	// Singleton socket connection for client communication
	clientConnection net.Conn
	clientMutex      sync.Mutex
)

func main() {
	// Start a TCP server for client communication
	go startSocketServer()

	// Start the HTTP server for WebRTC signaling
	http.HandleFunc("/offer", handleOffer)                // Handle SDP offers
	http.HandleFunc("/ice-candidate", handleIceCandidate) // Handle ICE candidates
	log.Println("WebRTC signaling server running on :8080")
	log.Fatal(http.ListenAndServe(":8080", nil))
}

// Start a singleton TCP socket server to communicate with the local client application
func startSocketServer() {
	listener, err := net.Listen("tcp", ":9000")
	if err != nil {
		log.Fatalf("Failed to start socket server: %v", err)
	}
	defer listener.Close()

	log.Println("Socket server running on :9000")

	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Printf("Error accepting connection: %v", err)
			continue
		}

		clientMutex.Lock()
		if clientConnection != nil {
			// If a client is already connected, reject the new connection
			log.Println("Client already connected. Rejecting new connection.")
			conn.Close()
			clientMutex.Unlock()
			continue
		}

		// Accept the new client connection
		clientConnection = conn
		clientMutex.Unlock()

		log.Println("Client connected:", conn.RemoteAddr())

		// Handle the client communication
		go handleClient(conn)
	}
}

// Handle communication with the connected client application
func handleClient(conn net.Conn) {
	defer func() {
		// Clean up connection when the client disconnects
		clientMutex.Lock()
		clientConnection = nil
		clientMutex.Unlock()
		conn.Close()
		log.Println("Client disconnected.")
	}()

	for {
		// Read incoming messages from the client
		buffer := make([]byte, 1024)
		n, err := conn.Read(buffer)
		if err != nil {
			log.Printf("Error reading from client: %v", err)
			break
		}

		message := string(buffer[:n])
		log.Printf("Message from client: %s", message)

		// Handle client commands (e.g., send to a peer, retrieve connection info, etc.)
		// Add your message handling logic here
	}
}

// Handle incoming SDP offer and initiate a WebRTC connection
func handleOffer(w http.ResponseWriter, r *http.Request) {
	var request struct {
		PeerID string                    `json:"peerId"`
		SDP    webrtc.SessionDescription `json:"sdp"`
	}

	// Parse the SDP offer and the peer ID
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request payload", http.StatusBadRequest)
		return
	}

	// Create a new PeerConnection for this peer
	peerConnection, err := createPeerConnection(request.PeerID)
	if err != nil {
		http.Error(w, "Failed to create PeerConnection", http.StatusInternalServerError)
		return
	}

	// Set the remote description from the received SDP
	if err := peerConnection.SetRemoteDescription(request.SDP); err != nil {
		http.Error(w, "Failed to set remote description", http.StatusInternalServerError)
		return
	}

	// Create an SDP answer
	answer, err := peerConnection.CreateAnswer(nil)
	if err != nil {
		http.Error(w, "Failed to create SDP answer", http.StatusInternalServerError)
		return
	}

	// Set the local description for the PeerConnection
	if err := peerConnection.SetLocalDescription(answer); err != nil {
		http.Error(w, "Failed to set local description", http.StatusInternalServerError)
		return
	}

	// Respond with the SDP answer
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(answer)
}

// Handle incoming ICE candidates
func handleIceCandidate(w http.ResponseWriter, r *http.Request) {
	var request struct {
		PeerID       string                  `json:"peerId"`
		ICECandidate webrtc.ICECandidateInit `json:"iceCandidate"`
	}

	// Parse the ICE candidate and the associated peer ID
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		http.Error(w, "Invalid request payload", http.StatusBadRequest)
		return
	}

	mutex.Lock()
	peerConnection, exists := peerConnections[request.PeerID]
	mutex.Unlock()

	if !exists {
		http.Error(w, "PeerConnection not found", http.StatusNotFound)
		return
	}

	// Add the ICE candidate to the PeerConnection
	if err := peerConnection.AddICECandidate(request.ICECandidate); err != nil {
		http.Error(w, "Failed to add ICE candidate", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// Create a new PeerConnection for a specific peer ID
func createPeerConnection(peerID string) (*webrtc.PeerConnection, error) {
	// WebRTC configuration with a STUN server for NAT traversal
	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{"stun:stun.l.google.com:19302"}},
		},
	}

	peerConnection, err := webrtc.NewPeerConnection(config)
	if err != nil {
		return nil, err
	}

	// Set up event handlers for the PeerConnection
	peerConnection.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c != nil {
			// Send ICE candidate back to the signaling server or client
			log.Printf("Generated ICE candidate for peer %s: %v", peerID, c.ToJSON())
		}
	})

	peerConnection.OnDataChannel(func(dc *webrtc.DataChannel) {
		log.Printf("New DataChannel from peer %s: %s", peerID, dc.Label())

		// Handle incoming messages on the data channel
		dc.OnMessage(func(msg webrtc.DataChannelMessage) {
			log.Printf("Message from peer %s: %s", peerID, string(msg.Data))
		})
	})

	// Store the PeerConnection in the map
	mutex.Lock()
	peerConnections[peerID] = peerConnection
	mutex.Unlock()

	return peerConnection, nil
}
