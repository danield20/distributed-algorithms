all: ring

ring:
	javac Ring.java

run:
	java Ring

clean:
	rm *.class