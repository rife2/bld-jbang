import java.io.FileWriter;
import java.io.IOException;

public class hello {
    public static void main(String[] args) throws IOException {
        FileWriter writer = new FileWriter(args[0]);
        writer.write("Hello World");
        writer.close();
    }
}