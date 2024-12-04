# **WUMP Client Implementation (HUMP Protocol)**

## **Project Description**
This project implements a client for the WUMP protocol, specifically the HUMP (WUMP with Handoff) variant. The program allows for reliable file transfer over UDP using a simplified version of TFTP. The client communicates with a server (`ulam.cs.luc.edu`) and handles various scenarios, including retransmissions, duplicate packets, and errors.

---

## **Features**
- **Basic File Transfer**:
  - Downloads files using the HUMP protocol.
  - Sends acknowledgments (`ACK`) for received data blocks.
- **Sanity Checks**:
  - Validates protocol, opcode, port, packet size, and block numbers.
- **Timeout Handling**:
  - Retransmits the last `ACK` if a response is not received within a timeout interval.
- **Dallying**:
  - Waits after receiving the last block to ensure the final `ACK` is delivered.
- **Error Handling**:
  - Sends error packets (`ERROR`) for invalid packets (e.g., wrong port).
- **Extra Credit (Optional)**:
  - Sliding windows for improved throughput.
  - Adaptive timeouts based on round-trip times (RTT).

---

## **Usage**

### **Compilation**
Compile the source code using the following command:
```bash
javac wclient.java wumppkt.java
```
## **Running the Client**

Run the client with the following syntax:

```bash
java wclient <filename> <winsize> <hostname>
```
### **Parameters**:
- **`<filename>`**: The file to request from the server (e.g., `vanilla`, `dupdata2`, `losedata2`).
- **`<winsize>`**: The sliding window size (use `1` for basic functionality).
- **`<hostname>`**: The server to connect to (e.g., `ulam.cs.luc.edu`).


## **Supported Test Cases**
This client has been tested against the following scenarios provided by the `ulam.cs.luc.edu` server:

- **`vanilla`**: Normal transfer.
- **`dupdata2`**: Duplicate `DATA[2]` packets.
- **`losedata2`**: `DATA[2]` is lost until `ACK[1]` is retransmitted.
- **`marspacket`**: A packet arrives from an invalid port.
- **`badopcode`**: A packet arrives with an invalid opcode.
- **`nofile`**: Server responds with a `NO FILE` error.
- **`lose`**: Simulates heavy data loss.
- **`spray`**: Server sends a barrage of `DATA[1]` packets.
- **`delay`**: Delayed transmission of the first packet.
- **`reorder`**: First windowful is sent out of order.

---

## **Program Flow**

### **Initialization**:
1. Sends a request (`REQ`) to the server for the specified file.
2. Receives a `HANDOFF` packet from the server with a new port.

### **File Transfer**:
1. Receives `DATA` packets in order.
2. Sends `ACK` for each valid `DATA` packet.
3. Retransmits `ACK` on timeouts if necessary.

### **Dallying**:
1. After the last block, waits to ensure the final `ACK` is received by the server.
2. Handles duplicate final `DATA` packets during this period.

### **Error Handling**:
1. Validates packets for correct protocol, opcode, port, size, and block number.
2. Sends `ERROR` packets for invalid data or unexpected sources.


## **Implementation Details**

### **Sanity Checks**:
- Ensures received packets meet all protocol requirements.
- Drops invalid or duplicate packets to maintain data integrity.

### **Timeout Handling**:
- Tracks the last valid packet time.
- Retransmits the last `ACK` if no response is received within the timeout interval.

### **Sliding Windows (Optional)**:
- Implements a configurable sliding window size for efficient file transfers.

### **Adaptive Timeouts (Optional)**:
- Dynamically adjusts timeout intervals based on round-trip time measurements.

---

## **File Structure**
- **`wclient.java`**: The main client implementation.
- **`wumppkt.java`**: Helper class for creating and parsing WUMP protocol packets.

---

## **Known Limitations**
- Sliding windows and adaptive timeouts are implemented as optional features but may not have been fully tested in all edge cases.
- The client assumes the server adheres to the HUMP protocol specification.

---

## **How to Test**
1. Compile the program using the provided commands.
2. Test the client against the supported scenarios using the `ulam.cs.luc.edu` server.
3. Verify correct file transfer and behavior for all test cases.

---

## **Acknowledgments**
- **Professor X**: For providing the project outline and server test cases.
- **WUMP Protocol Documentation**: For detailed specifications and protocol behavior.
