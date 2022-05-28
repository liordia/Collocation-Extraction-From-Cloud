import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class WordAndCounter implements WritableComparable<WordAndCounter> {

    String word_1;
    String word_2;
    double rightword_counter;
    int decade;

    WordAndCounter() {
        this.word_1 = "";
        this.word_2 = "";
        this.decade = -1;
        this.rightword_counter = -1;
    }

    public WordAndCounter(String word_1, String word_2, int decade, double rightword_counter) {
        this.word_1 = word_1;
        this.word_2 = word_2;
        this.rightword_counter = rightword_counter;
        this.decade = decade;
    }

    @Override
    public int compareTo(WordAndCounter other) {
        // First compare decades
        int ret = decade - other.decade;
        if (ret == 0)
            // Then compare left word
            ret = (int) (word_1.compareTo(other.word_1));
        if (ret == 0)
            // Only then compare right word
            ret = (int) (word_2.compareTo(other.word_2));
        return ret;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(word_1);
        out.writeUTF(word_2);
        out.writeDouble(rightword_counter);
        out.writeInt(decade);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        this.word_1 = in.readUTF();
        this.word_2 = in.readUTF();
        this.rightword_counter = in.readDouble();
        this.decade = in.readInt();
    }

    public String toString(){
        return String.format("%s\t%s\t%d", this.word_1, this.word_2, this.decade);
    }

    public String getFirstWord() {
        return word_1;
    }

    public String getSecondWord() {
        return word_2;
    }

    public int getDecade() {
        return decade;
    }

    public double getRightword_counter() { return rightword_counter; }
}
