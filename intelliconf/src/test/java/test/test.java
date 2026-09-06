package test;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.Object;

@Data
@NoArgsConstructor
class Student {

    private String name;
    private int age;

    public Student(String name, int age) {
        this.age = age;
        this.name = name;
    }
}

public class test {

    public static void main(String[] args) {
        Student a = new Student("abc", 123);
        Student b = a;

        if (a == b){
            System.out.println(true);
        } else {
            System.out.println(false);
        }

        b.setName("deq");
        System.out.println(a.getName());
        if (a == b){
            System.out.println(true);
        } else {
            System.out.println(false);
        }
    }
}
