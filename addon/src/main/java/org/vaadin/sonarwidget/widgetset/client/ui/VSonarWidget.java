package org.vaadin.sonarwidget.widgetset.client.ui;

import java.util.ArrayList;
import java.util.List;

import org.vaadin.sonarwidget.widgetset.client.ui.ImageRenderer.Listener;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

public class VSonarWidget extends ScrollPanel implements ScrollHandler {

    private DepthData model;
    private FlowPanel vert;
    private Label depthlabel;
    private Label templabel;
    private Label cursorlabel;
    private Canvas ruler;
    private VerticalPanel labels;

    private List<String> drawn;
    private List<ImageRenderer> renderers;
    private SonarWidgetState state;
    private SonarWidgetConnector connector;

    public static final int tilewidth = 400;

    public interface ClickListener {
        void onClick(int coordinate);
    }

    private ClickListener clickListener;

    public VSonarWidget() {
        super();

        this.drawn = new ArrayList<String>();
        this.depthlabel = new Label();
        this.templabel = new Label();
        this.cursorlabel = new Label();
        this.renderers = new ArrayList<ImageRenderer>();
        vert = new FlowPanel();
        vert.setHeight("100%");
        labels = new VerticalPanel();
        labels.getElement().getStyle().setPosition(Position.FIXED);
        labels.setStyleName("v-sonarwidget-labels");
        addStyleName("v-sonarwidget");

        setWidget(vert);
        vert.add(labels);
        labels.add(depthlabel);
        labels.add(cursorlabel);
        labels.add(templabel);
        getElement().getStyle().setOverflowX(Overflow.SCROLL);
        getElement().getStyle().setOverflowY(Overflow.HIDDEN);
        this.ruler = Canvas.createIfSupported();
        this.ruler.getElement().getStyle().setPosition(Position.FIXED);
        this.ruler.addStyleName("v-sonarwidget-ruler");

        sinkEvents(Event.ONMOUSEMOVE);

        ruler.sinkEvents(Event.ONMOUSEUP);
        ruler.addMouseUpHandler(new MouseUpHandler() {

            @Override
            public void onMouseUp(MouseUpEvent event) {
                onMouseClick(getMouseCursorPoint(event.getClientX(), event.getClientY()));
            }
        });

        addScrollHandler(this);

        Scheduler.get().scheduleEntry(new RepeatingCommand() {

            @Override
            public boolean execute() {
                if (getElement().getClientWidth() <= 0) {
                    return true;
                }

                if (drawn.isEmpty()) {
                    fetchSonarData(0);
                }
                return false;
            }
        });
    }

    public void setListener(ClickListener listener) {
        this.clickListener = listener;
    }

    public void setDirty() {
        for (ImageRenderer renderer : renderers) {
            renderer.setOverlay(!this.state.overlay);
            renderer.setOverlay(this.state.overlay);
        }

        onScroll(null);
    }

    public void setState(SonarWidgetState state) {
        this.state = state;
        for (ImageRenderer renderer : renderers) {
            renderer.setOverlay(state.overlay);
            renderer.setColor(state.color);
            renderer.setSidescan(state.sidescan);
        }

        render(getHorizontalScrollPosition());
    }

    public void setConnector(SonarWidgetConnector connector) {
        this.connector = connector;
    }

    public void initializeCanvases(int pingcount) {
        if (model == null) {
            model = new DepthData(pingcount);
            vert.clear();
            vert.setWidth(pingcount + "px");
            vert.getElement().getStyle().setPosition(Position.RELATIVE);
            vert.add(labels);
            vert.add(ruler);
            labels.getElement().getStyle().setZIndex(10);
            ruler.getElement().getStyle().setZIndex(10);
            labels.setVisible(false);
            ruler.setVisible(false);
        }
    }

    public void setOffset(int offset, String pic, String[] lowlimits,
            String[] depths, String[] temps) {
        model.appendLowlimit(lowlimits, offset);
        model.appendDepth(depths, offset);
        model.appendTemp(temps, offset);

        Listener listener = new ImageRenderer.Listener() {

            @Override
            public void doneLoading(int offset) {
                render(offset);
            }
        };

        ImageRenderer renderer = new ImageRenderer(pic, model,
                VSonarWidget.this, offset, depths.length, listener);
        renderer.setOverlay(state.overlay);
        renderer.setColor(state.color);
        renderer.setSidescan(state.sidescan);
        this.vert.add(renderer.getCanvas());

        renderer.clearCanvas();
        renderers.add(renderer);
    }

    private void fetchSonarData(int offset) {
        int normalizedoffset = offset - offset % tilewidth;

        for (int loop = normalizedoffset; loop < normalizedoffset
                + getOffsetWidth() + tilewidth; loop += tilewidth) {
            if (this.drawn.contains(new Integer(loop).toString())) {
                continue;
            } else {
                this.drawn.add(new Integer(loop).toString());
            }

            connector.getData(getElement().getClientHeight(), tilewidth, loop);
        }
    }

    public void render(int offset) {
        float range = state.range;

        // Let 0 be autorange.
        if (range == 0) {
            for (ImageRenderer renderer : renderers) {
                if (renderer.isVisible(offset)) {
                    if (renderer.getMaxDepthArea() > range) {
                        range = renderer.getMaxDepthArea();
                    }
                }
            }
        }

        for (ImageRenderer renderer : renderers) {
            if (renderer.isVisible(offset)) {
                renderer.setRange(range);
                renderer.render();
            }
        }
    }

    public ImageRenderer getRenderer(int offset) {
        for (ImageRenderer renderer : renderers) {
            if (renderer.isCurrentRenderer(offset)) {
                return renderer;
            }
        }

        return null;
    }

    private void updateRuler(int coordinate) {
        this.ruler.setVisible(true);
        int height = getElement().getClientHeight();
        this.ruler.setCoordinateSpaceHeight(height);
        this.ruler.setCoordinateSpaceWidth(10);
        this.ruler.setWidth("10px");
        this.ruler.setHeight(height + "px");

        Context2d context2d = this.ruler.getContext2d();
        context2d.clearRect(0, 0, 10, height);
        context2d.setFillStyle("blue");
        context2d.fillRect(0, 0, 1, height);

        // Skip depth arrow in side scan mode
        if (!state.sidescan) {
            float depth = model.getDepth(coordinate);
            float lowlimit = model.getLowlimit(coordinate);
            int drawdepth = (int) (height * depth / lowlimit);
            drawdepth = getRenderer(coordinate).mapToDepth(coordinate,
                    drawdepth);
            context2d.beginPath();
            context2d.moveTo(10, drawdepth - 10);
            context2d.lineTo(1, drawdepth);
            context2d.lineTo(10, drawdepth + 10);
            context2d.stroke();
        }

        this.ruler
                .getElement()
                .getStyle()
                .setMarginLeft(coordinate - getHorizontalScrollPosition(),
                        Unit.PX);
    }

    @Override
    public void onScroll(ScrollEvent event) {
        // init lazy loading
        fetchSonarData(getHorizontalScrollPosition());
        render(getHorizontalScrollPosition());
    }

    private void onMouseHover(Point coordinate) {
        updateRuler(coordinate.getX());
        updateTextLabels(coordinate);
    }

    private void onMouseClick(Point coordinate) {
        if (clickListener != null) {
            clickListener.onClick(coordinate.getX());
        }
    }

    private void updateTextLabels(Point coordinate) {
        this.labels.setVisible(true);
        this.labels
                .getElement()
                .getStyle()
                .setMarginLeft(
                        coordinate.getX() - getHorizontalScrollPosition(),
                        Unit.PX);

        this.depthlabel.setText("Depth: " + model.getDepth(coordinate.getX())
                + " m");
        float cursor = getRenderer(coordinate.getX()).getRange()
                * (coordinate.getY() / (float) getElement().getClientHeight());


        if (state.sidescan) {
            float surfacepx = (float) getElement().getClientHeight() / 2;
            float distancepx = Math.abs(surfacepx - coordinate.getY());
            cursor = distancepx * getRenderer(coordinate.getX()).getRange()
                    / surfacepx;
        }
        this.cursorlabel.setText("Cursor: "
                + NumberFormat.getFormat("#.0 m").format(cursor));
        this.templabel.setText("Temp: " + model.getTemp(coordinate.getX())
                + " C");
    }

    @Override
    public void onBrowserEvent(Event event) {
        switch (DOM.eventGetType(event)) {
        case Event.ONMOUSEMOVE:
            onMouseHover(getMouseCursorPoint(event.getClientX(),
                    event.getClientY()));
            break;
        default:
            super.onBrowserEvent(event);
            break;
        }
    }

    private Point getMouseCursorPoint(int clientX, int clientY) {
        Point pt = new Point(clientX - getAbsoluteLeft()
                + getHorizontalScrollPosition(), clientY
                - getAbsoluteTop());
        return pt;
    }

    private static class Point {
        int x;
        int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }
    }

}
