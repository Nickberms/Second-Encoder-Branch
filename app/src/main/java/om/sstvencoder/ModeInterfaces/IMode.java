package om.sstvencoder.ModeInterfaces;

public interface IMode {
    void init();

    boolean process();

    void finish(boolean cancel);
}