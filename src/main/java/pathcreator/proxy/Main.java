package pathcreator.proxy;

import pathcreator.proxy.uid.Uid32;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        Uid32.setMachineId(2);
        final byte[] buffer = Uid32.generate();
        System.out.println(Arrays.toString(buffer));
    }
}