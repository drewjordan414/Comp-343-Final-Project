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
### ***Running the Client**
Run the client with the following syntax:
```bash
java wclient <filename> <winsize> <hostname>
```
```bash
<filename>: The file to request from the server (e.g., vanilla).
<winsize>: The sliding window size (use 1 for basic functionality).
```