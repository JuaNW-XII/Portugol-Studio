package br.univali.ps.ui.carrossel;

import br.univali.ps.nucleo.Configuracoes;
import br.univali.ps.ui.paineis.ImagePanel;
import br.univali.ps.ui.swing.ColorController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public class CarrosselCursos extends JPanel {

    private Logger LOGGER = Logger.getLogger(CarrosselCursos.class.getName());

    private Timer timer;

    private int cursoAtual = -1;

    private List<Curso> cursos = Collections.EMPTY_LIST;

    private final int MARGEM_LAYOUT = 10;

    private final ImagePanel painelImagem;
    private final JLabel labelTitulo;
    private final JLabel labelDescricao;

    private static final Color TRANSPARENT_BLACK = new Color(0, 0, 0, 0);
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 220);

    private final Collection<CarrosselListener> listeners = new ArrayList<>();

    private final String PACOTE_RESOURCES = "br/univali/ps/carrossel/";

    public CarrosselCursos() {

        painelImagem = new ImagePanel();
        labelTitulo = new JLabel();
        labelDescricao = new JLabel();

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter parentListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                CarrosselCursos.this.processMouseEvent(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                CarrosselCursos.this.processMouseMotionEvent(e);
            }

        };

        labelTitulo.addMouseListener(parentListener);
        labelTitulo.addMouseMotionListener(parentListener);
        labelDescricao.addMouseListener(parentListener);
        labelDescricao.addMouseMotionListener(parentListener);
        painelImagem.addMouseListener(parentListener);
        painelImagem.addMouseMotionListener(parentListener);

        MouseListener mouseListener = new MouseListener();
        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension tamanho = new Dimension((int) (getWidth() * 0.5), -1);
                painelImagem.setMinimumSize(tamanho);
                painelImagem.setPreferredSize(tamanho);
            }
        });

        setLayout(new BorderLayout(MARGEM_LAYOUT, MARGEM_LAYOUT));

        add(labelTitulo, BorderLayout.NORTH);
        add(labelDescricao, BorderLayout.CENTER);
        add(painelImagem, BorderLayout.EAST);

        labelTitulo.setForeground(ColorController.COR_LETRA);
        labelDescricao.setForeground(ColorController.COR_LETRA);

        labelTitulo.setFont(getFont().deriveFont(17f));
        labelDescricao.setFont(getFont().deriveFont(13f));

        setBackground(ColorController.COR_DESTAQUE);

        try {
            carregaCursos(getInputStreamCursos());

            inicializaTimer();

        } catch (Exception ex) {
            ex.printStackTrace();
            setVisible(false);
        }

    }

    private void inicializaTimer() {
        int delayInicial = 500;
        timer = new Timer(delayInicial, (e) -> {
            try {
                mostraProximoCurso();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        timer.setDelay(cursos.isEmpty() ? 2000 : cursos.get(0).getTempoExibicao());
        timer.start();
    }

    private class MouseListener extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            int arrowsBackgroundWidth = getLarguraBackgroundSetas();
            if (mouseEstaNasBordas(arrowsBackgroundWidth)) {
                try {
                    if (e.getX() <= arrowsBackgroundWidth) { // mouse in left side 
                        mostraCursoAnterior();
                    } else {
                        mostraProximoCurso();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else {
                abreLinkCursoAtual();
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            repaint();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
            repaint();
        }

    }

    private String getBaseUrl() {
        if (Configuracoes.rodandoEmDesenvolvimento()) {
            return "";
        }
        
        return "https://raw.githubusercontent.com/UNIVALI-LITE/Portugol-Studio/master/ide/src/main/resources/";
    }
    
    private InputStream getInputStreamCursos() throws Exception {
        if (Configuracoes.rodandoEmDesenvolvimento()) {
            LOGGER.log(Level.INFO, "Lendo 'cursos.json' do JAR!");
            return ClassLoader.getSystemClassLoader().getResourceAsStream(PACOTE_RESOURCES + "cursos.json");
        }

        LOGGER.log(Level.INFO, "Lendo 'cursos.json' do github!");

        URL url = new URL(getBaseUrl() + PACOTE_RESOURCES + "cursos.json");
        return url.openStream();
    }

    public void addListener(CarrosselListener listener) {
        listeners.add(listener);
    }

    private void carregaCursos(InputStream stream) {

        if (stream == null) {
            throw new IllegalArgumentException("O stream está nulo!");
        }

        if (cursos.size() > 0) { // cursos já estão carregados?
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            cursos = Arrays.asList(mapper.readValue(stream, Curso[].class));

            for (CarrosselListener listener : listeners) {
                listener.cursosCarregados();
            }

        } catch (IOException e) {
            for (CarrosselListener listener : listeners) {
                listener.erroNoCarregamentoCursos();
            }
            e.printStackTrace();
        }

    }

    private void abreLinkCursoAtual() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Curso curso = cursos.get(cursoAtual);
            try {
                Desktop.getDesktop().browse(new URI(curso.getLink()));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void mostraCursoAnterior() throws IOException {
        
        cursoAtual = (cursoAtual + cursos.size() - 1) % cursos.size();

        Curso curso = cursos.get(cursoAtual);

        mostraCurso(curso);
    }

    private void mostraProximoCurso() throws IOException {

        cursoAtual = (cursoAtual + 1) % cursos.size();

        Curso curso = cursos.get(cursoAtual);

        mostraCurso(curso);
    }

    private void mostraCurso(Curso curso) throws IOException {
        String caminhoImagem = getBaseUrl() + PACOTE_RESOURCES + curso.getCaminhoImagem();
        InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(caminhoImagem);

        labelTitulo.setText("<html> " + curso.getTitulo() + "</html>");
        labelDescricao.setText("<html> " + curso.getDescricao() + "</html>");
        painelImagem.setImagem(ImageIO.read(stream));

        //doLayout();
        if (timer != null) {
            timer.setDelay(curso.getTempoExibicao());

            if (!timer.isRunning()) {
                timer.start();
            }
        }
    }

    private void desenhaSetas(Graphics2D g2d, int larguraBackgroundSetas) {
        final Color arrowsColor = new Color(255, 255, 255, 230);
        final int arrowSize = (int) (larguraBackgroundSetas * 0.5);

        g2d.setColor(arrowsColor);

        Polygon setaEsquerda = new Polygon();
        setaEsquerda.addPoint(0, 0);
        setaEsquerda.addPoint(arrowSize, -arrowSize);
        setaEsquerda.addPoint(arrowSize, arrowSize);

        setaEsquerda.translate(5, getHeight() / 2);

        Polygon setaDireita = new Polygon();
        setaDireita.addPoint(getWidth(), 0);
        setaDireita.addPoint(getWidth() - arrowSize, -arrowSize);
        setaDireita.addPoint(getWidth() - arrowSize, arrowSize);

        setaDireita.translate(-5, getHeight() / 2);

        g2d.fillPolygon(setaEsquerda);
        g2d.fillPolygon(setaDireita);
    }

    private void desenhaBackgroundSetas(Graphics2D g2d, int larguraBackgroundBotoes) {

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gradienteSetaEsquerda = new GradientPaint(0, 0, BACKGROUND_COLOR, larguraBackgroundBotoes, 0, TRANSPARENT_BLACK);
        g2d.setPaint(gradienteSetaEsquerda);
        g2d.fillRect(0, 0, larguraBackgroundBotoes * 2, getHeight());

        GradientPaint gradienteSetaDireita = new GradientPaint(getWidth() - larguraBackgroundBotoes, 0,
                TRANSPARENT_BLACK, getWidth(), 0, BACKGROUND_COLOR);
        g2d.setPaint(gradienteSetaDireita);
        g2d.fillRect(getWidth() - larguraBackgroundBotoes * 2, 0, larguraBackgroundBotoes * 2, getHeight());
    }

    private int getLarguraBackgroundSetas() {
        return (int) (getWidth() * 0.15f);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (isVisible()) {
            final int larguraBackgroundSetas = getLarguraBackgroundSetas();
            if (mouseEstaNasBordas(larguraBackgroundSetas)) {
                Graphics2D g2d = (Graphics2D) g;
                desenhaBackgroundSetas(g2d, larguraBackgroundSetas);
                desenhaSetas(g2d, larguraBackgroundSetas);
            }
        }
    }

    private boolean mouseEstaNasBordas(int arrowsBackgroundWidth) {
        Point mousePosition = getMousePosition();
        if (mousePosition != null) {
            double mouseX = getMousePosition().getX();
            return mouseX < arrowsBackgroundWidth || mouseX >= getWidth() - arrowsBackgroundWidth;
        }

        return false;
    }
}
