package core.main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;


sealed abstract class Out {
    abstract void println(String line);
    abstract void printTemp(String line);
    abstract void errPrintln(String line);
    abstract void errPrintTemp(String line);
    void close() {}

    static Out create() {
        return System.console() == null ? new Window() : new Std();
    }

    private static final class Std extends Out {
        @Override void println(String line)     { System.out.println(line); }
        @Override void printTemp(String line)   { System.out.print(line + '\r'); }
        @Override void errPrintln(String line)  { System.err.println(line); }
        @Override void errPrintTemp(String line){ System.err.print(line + '\r'); }
    }

    private static final class Window extends Out {
        private boolean newLine = true;
        private DefaultListModel<Line> model = null;
        private JFrame frame = null;

        @Override void println(String line)     { print(new DefaultLine(line), true); }
        @Override void printTemp(String line)   { print(new DefaultLine(line), false); }
        @Override void errPrintln(String line)  { print(new ErrorLine(line), true); }
        @Override void errPrintTemp(String line){ print(new ErrorLine(line), false); }
        @Override void close() {
            SwingUtilities.invokeLater(() -> {
                if (frame != null) frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
            });
        }

        private void print(Line line, boolean newLine) {
            SwingUtilities.invokeLater(() -> printUnsafe(line, newLine));
        }
        private void printUnsafe(Line line, boolean newLine) {
            if (model == null) {
                model = new DefaultListModel<>();

                JList<Line> list = new JList<>(model);
                list.setBackground(new Color(32, 0, 32));
                list.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
                list.setCellRenderer(new Renderer());

                frame = new JFrame();
                frame.add(new JScrollPane(list));
                frame.setSize(1200, 400);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            }

            if (this.newLine) model.addElement(line);
            else model.set(model.size() - 1, line);
            this.newLine = newLine;
        }

        private static sealed abstract class Line {
            private final String value;
            abstract Color color();
            Line(String value) { this.value = value; }
            @Override public String toString() { return value; }
        }
        private static final class DefaultLine extends Line {
            DefaultLine(String value) { super(value); }
            Color color() { return color; }
            private static final Color color = new Color(208, 208, 208);
        }
        private static final class ErrorLine extends Line {
            ErrorLine(String value) { super(value); }
            Color color() { return color; }
            private static final Color color = new Color(215, 10, 10);
        }

        private static class Renderer extends DefaultListCellRenderer {
            @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus
            ) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected && value instanceof Line line)
                    setForeground(line.color());
                return this;
            }
        }
    }

}