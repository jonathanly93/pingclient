### HOW TO RUN

Example startup:
1) javac *.java
2) Open a terminal and enter "java PingServer --port=8080 --loss_rate=.25 --avg_delay=100"
3) Open second terminal and enter "java PingClient --server_ip=localhost --server_port=8080 --count=100 --period=10 --timeout=100"
4) Review over the results

## Note
You can adjust the argmuents for hte PingClient and PingServer to change the results.
