import java.util.ArrayList;

public class Test {

    private static ArrayList<Integer> a;
    private ArrayList<Integer> b;

    public static void main(String[] args) {
        Test t = new Test();
        t.runAdd();
        System.out.println(a);
        System.out.println(t.b);
    }

    private void add(ArrayList<Integer> arr) {
        arr = new ArrayList<>();
        arr.add(1);
    }

    private void runAdd() {
        add(a);
        add(b);
    }
}
