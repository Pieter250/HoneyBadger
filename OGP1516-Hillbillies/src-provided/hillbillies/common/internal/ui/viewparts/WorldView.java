package hillbillies.common.internal.ui.viewparts;

import hillbillies.common.internal.inputmodes.UserInputHandler;
import hillbillies.common.internal.options.HillbilliesOptions;
import hillbillies.common.internal.ui.sprites.AbstractSprite;
import hillbillies.common.internal.ui.viewmodel.ViewModel;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

public class WorldView {

	private final Pane root;
	protected final Pane[] tilePanels;
	protected final Pane[] spritePanels;

	private final Pane gridPanel;

	private final Rectangle selectionRectangle = new Rectangle(0, 0);

	private final ViewModel viewModel;

	private int optionShowUnderlying = -1;

	private HillbilliesOptions options;
	private UserInputHandler userInputHandler;

	public static WorldView create(ViewModel viewModel, HillbilliesOptions options) {
		WorldView result = new WorldView(viewModel, options);
		result.setupViewModel();
		return result;
	}

	public HillbilliesOptions getOptions() {
		return options;
	}

	protected WorldView(ViewModel viewModel, HillbilliesOptions options) {
		this.viewModel = viewModel;
		this.options = options;

		double viewWidth = viewModel.viewWidthProperty().get();
		double viewHeight = viewModel.viewHeightProperty().get();

		this.root = new Pane();
		root.setPickOnBounds(false);
		root.setMinSize(viewWidth, viewHeight);
		root.setPrefSize(viewWidth, viewHeight);
		root.setMaxSize(viewWidth, viewHeight);
		root.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
		root.setClip(new Rectangle(viewWidth, viewHeight));

		Pane[][] panels = setupPanels();
		tilePanels = panels[0];
		spritePanels = panels[1];
		setPanelEffects();

		if (getOptions().gridEnabled().getValue()) {
			gridPanel = new Pane();
			gridPanel.setMouseTransparent(true);
			root.getChildren().add(root.getChildren().size() - 1, gridPanel);
			createGrid();
		} else {
			gridPanel = null;
		}

		setupSelection();
		setupScroll();
	}

	protected void setupViewModel() {
		viewModel.addRefreshListener(this::updateTileInfo);
		viewModel.addNewSpriteListener(this::attachNewSprite);

		for (int visibleX = 0; visibleX < viewModel.getNbVisibleTilesX(); visibleX++) {
			for (int visibleY = 0; visibleY < viewModel.getNbVisibleTilesY(); visibleY++) {
				int visibleZ = viewModel.calculateVisibleZFromMap(visibleX, visibleY);
				updateTileInfo(visibleX, visibleY, visibleZ);
			}
		}
		for (AbstractSprite<?, ?> sprite : viewModel.getVisibleSprites()) {
			attachNewSprite(sprite);
		}
	}

	protected void updateTileInfo(int visibleX, int visibleY, int visibleZ) {
	}

	protected int getMaxDepth() {
		int maxDepth = optionShowUnderlying + 1;
		if (optionShowUnderlying < 0) {
			maxDepth = viewModel.getMaxZLevel() + 1;
		}
		return maxDepth;
	}

	private Pane[][] setupPanels() {
		int nPanels = getMaxDepth() + 1;
		Pane[] tilePanels = new Pane[nPanels];
		Pane[] spritePanels = new Pane[nPanels];
		for (int depth = getMaxDepth(); depth >= 0; depth--) {
			Pane tilePanel = new Pane();
			tilePanels[depth] = tilePanel;
			Pane spritePanel = new Pane();
			spritePanels[depth] = spritePanel;
			tilePanel.setPickOnBounds(false);
			spritePanel.setPickOnBounds(false);
			root.getChildren().add(tilePanel);
			root.getChildren().add(spritePanel);
		}
		return new Pane[][] { tilePanels, spritePanels };
	}

	private void setupScroll() {
		root.setOnScroll(e -> {
			double dx = e.getDeltaX();
			double dy = e.getDeltaY();
			if (getOptions().reverseScrollEnabled().getValue()) {
				dx = -dx;
				dy = -dy;
			}
			if (e.isShiftDown()) {
				int dz = -1;
				if (dx > 0 || dy > 0) {
					dz = +1;
				}
				viewModel.adjustLevel(dz);
				e.consume();
			} else {
				viewModel.moveOrigin(dx, dy);
				e.consume();
			}
		});

	}

	private void setupSelection() {
		selectionRectangle.setManaged(false);

		// Color.CORNFLOWERBLUE
		// Color.LIGHTGREEN
		selectionRectangle.setFill(Color.CORNFLOWERBLUE.deriveColor(0, 1, 1, 0.3));
		selectionRectangle.setStroke(Color.CORNFLOWERBLUE);
		selectionRectangle.setVisible(false);
		root.getChildren().add(selectionRectangle);

		EventHandler<MouseEvent> selectionHandler = new EventHandler<MouseEvent>() {
			private Point2D panStart;

			@Override
			public void handle(MouseEvent e) {
				if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
					if (e.getButton() == MouseButton.PRIMARY) {
						selectionRectangle.setUserData(new Point2D(e.getX(), e.getY()));
						selectionRectangle.relocate(e.getX(), e.getY());
						selectionRectangle.setHeight(0);
						selectionRectangle.setWidth(0);
						e.consume();
					} else if (e.getButton() == MouseButton.MIDDLE) {
						panStart = new Point2D(e.getX(), e.getY());
						e.consume();
					}
				} else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED) {
					if (e.getButton() == MouseButton.PRIMARY) {
						selectionRectangle.setVisible(true);
						Point2D initRect = (Point2D) selectionRectangle.getUserData();
						if (initRect != null) {
							double w = e.getX() - initRect.getX();
							double h = e.getY() - initRect.getY();
							if (w < 0) {
								selectionRectangle.setLayoutX(initRect.getX() + w);
							}
							if (h < 0) {
								selectionRectangle.setLayoutY(initRect.getY() + h);
							}
							selectionRectangle.setWidth(Math.abs(w));
							selectionRectangle.setHeight(Math.abs(h));
						}
						e.consume();
					} else if (e.getButton() == MouseButton.MIDDLE) {
						// pan
						if (panStart != null) {
							double dx = panStart.getX() - e.getX();
							double dy = panStart.getY() - e.getY();
							panStart = new Point2D(e.getX(), e.getY());
							viewModel.moveOrigin(dx * getPixelsPerTile() / 3, dy * getPixelsPerTile() / 3);
							e.consume();
						}
					}
				} else if (e.getEventType() == MouseEvent.MOUSE_RELEASED) {
					if (e.getButton() == MouseButton.PRIMARY && selectionRectangle.isVisible()) {
						regionSelected(selectionRectangle.getBoundsInParent(), e);
						selectionRectangle.setUserData(null);
						selectionRectangle.setVisible(false);
						e.consume();
					} else if (e.getButton() == MouseButton.MIDDLE) {
						this.panStart = null;
						e.consume();
					}
				} else if (e.getEventType() == MouseEvent.MOUSE_CLICKED && e.getButton() == MouseButton.PRIMARY) {
					handleClick(e);
					e.consume();
				}
			};
		};
		root.addEventHandler(MouseEvent.ANY, selectionHandler);
	}

	protected void handleClick(MouseEvent e) {
		getUserInputHandler().worldPointClicked(viewModel.screenToWorldX(e.getX()), viewModel.screenToWorldY(e.getY()),
				viewModel.screenToWorldZ(e.getX(), e.getY()), e);
	}

	public UserInputHandler getUserInputHandler() {
		return userInputHandler;
	}

	public void setUserInputHandler(UserInputHandler userInputHandler) {
		this.userInputHandler = userInputHandler;
	}

	protected void regionSelected(Bounds screenBounds, MouseEvent e) {
		getUserInputHandler().regionSelected(viewModel.screenToWorldX(screenBounds.getMinX()),
				viewModel.screenToWorldY(screenBounds.getMinY()), viewModel.getCurrentZLevel(),
				viewModel.screenToWorldX(screenBounds.getMaxX()), viewModel.screenToWorldY(screenBounds.getMaxY()),
				viewModel.getCurrentZLevel() + 1, e);
	}

	private void createGrid() {
		int tileSize = getPixelsPerTile();

		Color gridColor = Color.BLANCHEDALMOND.deriveColor(0, 1, 1, 0.2);
		for (int x = 0; x <= viewModel.getNbVisibleTilesX(); x++) {
			Line line = new Line(x * tileSize, 0, x * tileSize, viewModel.getNbVisibleTilesY() * tileSize);
			line.setStroke(gridColor);
			gridPanel.getChildren().add(line);
		}
		for (int y = 0; y <= viewModel.getNbVisibleTilesY(); y++) {
			Line line = new Line(0, y * tileSize, viewModel.getNbVisibleTilesX() * tileSize, y * tileSize);
			line.setStroke(gridColor);
			gridPanel.getChildren().add(line);
		}
		if (getOptions().showGridCoordinatesEnabled().getValue()) {
			for (int x = 0; x <= viewModel.getNbVisibleTilesX(); x++) {
				for (int y = 0; y <= viewModel.getNbVisibleTilesY(); y++) {
					Label label = new Label();
					label.setFont(Font.font(8));
					label.setTextFill(Color.WHITE);
					gridPanel.getChildren().add(label);
					label.setLayoutX(x * tileSize);
					label.setLayoutY(y * tileSize);
					label.textProperty().bind(Bindings.format("%d,%d", viewModel.xTileOffsetProperty().add(x),
							viewModel.yTileOffsetProperty().add(y)));
				}
			}
		}

	}

	public ViewModel getViewModel() {
		return viewModel;
	}

	public int getPixelsPerTile() {
		return viewModel.getPixelsPerTile();
	}

	public Pane getRoot() {
		return root;
	}

	private void setPanelEffects() {
		if (getOptions().shadowEnabled().getValue()) {
			tilePanels[0].setEffect(new DropShadow(20, Color.BLACK));
		} else {
			tilePanels[0].setEffect(null);
		}
		spritePanels[0].setEffect(null);

		for (int depth = 1; depth < tilePanels.length; depth++) {
			Effect effect = null;
			if (getOptions().darkenEnabled().getValue()) {
				ColorAdjust adj = new ColorAdjust();
				adj.setBrightness(-0.3 * depth);
				effect = adj;
			}
			if (getOptions().blurEnabled().getValue()) {
				double r = depth * 5.0;
				BoxBlur blur = new BoxBlur(r, r, 3);
				blur.setInput(effect);
				effect = blur;
			}
			tilePanels[depth].setEffect(effect);
			spritePanels[depth].setEffect(effect);
		}
	}

	protected void attachNewSprite(AbstractSprite<?, ?> newSprite) {
		newSprite.depthProperty().addListener(e -> updateSpriteParent(newSprite));
		newSprite.getGraph().layoutXProperty().bind(newSprite.screenXProperty());
		newSprite.getGraph().layoutYProperty().bind(newSprite.screenYProperty());

		newSprite.getGraph().setOnMouseClicked(e -> {
			// can only select in current z-slice
			if (e.getButton() == MouseButton.PRIMARY && newSprite.depthProperty().get() == 0) {
				getUserInputHandler().objectClicked(newSprite.getObject(), e);
				e.consume();
			}
		});
		updateSpriteParent(newSprite);
	}

	protected void updateSpriteParent(AbstractSprite<?, ?> sprite) {
		int depthToShow = sprite.depthProperty().get();
		Node node = sprite.getGraph();
		if (node.getParent() != null
				&& (depthToShow < 0 || depthToShow > getMaxDepth() || node.getParent() != spritePanels[depthToShow])) {
			((Pane) node.getParent()).getChildren().remove(node);
		}

		if (depthToShow >= 0 && depthToShow <= getMaxDepth() && node.getParent() != spritePanels[depthToShow]) {
			spritePanels[depthToShow].getChildren().add(node);
		}
	}
}
