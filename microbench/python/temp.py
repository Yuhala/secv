from datetime import datetime



def multiHello(n):
    for i in range(0,n):
        print("<<< --- Hello Graal.Python ---->>>>");
    

start = datetime.now()
multiHello(2);
stop = datetime.now()
diff = stop - start
total_ms = diff.total_seconds()*1000

print("Runtime -------------->>>>>: ",total_ms,"ms")