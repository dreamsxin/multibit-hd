package org.multibit.hd.ui.views;

import com.google.common.base.Preconditions;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.ui.MultiBitUI;
import org.multibit.hd.ui.languages.Languages;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.themes.Themes;
import org.multibit.hd.ui.views.wizards.Wizards;
import org.multibit.hd.ui.views.wizards.welcome.WelcomeWizardState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * <p>View to provide the following to application:</p>
 * <ul>
 * <li>Provision of components and layout for the main frame</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class MainView extends JFrame {

  private static final Logger log = LoggerFactory.getLogger(MainView.class);

  private HeaderView headerView;
  private SidebarView sidebarView;
  private DetailView detailView;
  private FooterView footerView;

  // Need to track if a wizard was showing before a refresh occurred
  private boolean showExitingWelcomeWizard = false;
  private boolean showExitingPasswordWizard = false;

  public MainView() {

    CoreServices.uiEventBus.register(this);

    // Define the minimum size for the frame
    setMinimumSize(new Dimension(MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT));

    // Provide all panels with a reference to the main frame
    Panels.applicationFrame = this;

    setTitle(Languages.safeText(MessageKey.APPLICATION_TITLE));

    // Parse the configuration
    resizeToLastFrameBounds();

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    addComponentListener(new ComponentAdapter() {

      @Override
      public void componentMoved(ComponentEvent e) {
        updateConfiguration();
      }

      @Override
      public void componentResized(ComponentEvent e) {
        updateConfiguration();
      }

      /**
       * Keep the current configuration updated
       */
      private void updateConfiguration() {
        Rectangle bounds = getBounds();
        String lastFrameBounds = String.format("%d,%d,%d,%d", bounds.x, bounds.y, bounds.width, bounds.height);
        Configurations.currentConfiguration.getApplication().setLastFrameBounds(lastFrameBounds);
      }
    });

  }

  /**
   * <p>Resize the frame to the last bounds</p>
   */
  private void resizeToLastFrameBounds() {

    Rectangle newBounds = new Rectangle(0, 0, MultiBitUI.UI_MIN_WIDTH, MultiBitUI.UI_MIN_HEIGHT);

    String frameDimension = Configurations.currentConfiguration.getApplication().getLastFrameBounds();
    if (frameDimension != null) {
      String[] lastFrameDimension = frameDimension.split(",");
      if (lastFrameDimension.length == 4) {
        try {
          int x = Integer.valueOf(lastFrameDimension[0]);
          int y = Integer.valueOf(lastFrameDimension[1]);
          int w = Integer.valueOf(lastFrameDimension[2]);
          int h = Integer.valueOf(lastFrameDimension[3]);
          newBounds = new Rectangle(x, y, w, h);
          System.out.println("Rectangle: " + newBounds.toString());
        } catch (NumberFormatException e) {
          log.error("Incorrect format in configuration - using defaults");
        }
      }
    }

    // Place the frame in the desired position (setBounds() does not work)
    setLocation(newBounds.x, newBounds.y);
    setPreferredSize(new Dimension(newBounds.width, newBounds.height));

  }

  /**
   * <p>Rebuild the contents of the main view based on the current configuration and theme</p>
   */
  public void refresh() {

    log.debug("Refreshing MainView");

    Preconditions.checkState(SwingUtilities.isEventDispatchThread(), "Must be in the EDT. Check MainController.");

    // Clear out all the old content and rebuild it from scratch
    getContentPane().removeAll();
    getContentPane().add(createMainContent());

    // Catch up on recent events
    CoreServices.getApplicationEventService().repeatLatestEvents();

    // Ensure no light boxes are showing
    Panels.hideLightBoxIfPresent();

    // Check for any wizards that were showing before the refresh occurred
    if (showExitingWelcomeWizard) {

      Panels.showLightBox(Wizards.newExitingWelcomeWizard(WelcomeWizardState.WELCOME_SELECT_LANGUAGE).getWizardScreenHolder());

    } else if (showExitingPasswordWizard) {

      // Force an exit if the user can't get through
      Panels.showLightBox(Wizards.newExitingPasswordWizard().getWizardScreenHolder());

    } else {

      log.debug("Showing detail view");

      // No wizards so this reset is a settings change
      detailViewAfterWalletOpened();

    }

    // Tidy up and show
    pack();
    setVisible(true);

  }

  /**
   * @return The contents of the main panel (header, body and footer)
   */
  private JPanel createMainContent() {

    // Create the main panel and place it in this frame
    MigLayout layout = new MigLayout(
      Panels.migXYLayout(),
      "[]", // Columns
      "[][][]"  // Rows
    );
    JPanel mainPanel = Panels.newPanel(layout);

    // Set the overall tone
    mainPanel.setBackground(Themes.currentTheme.headerPanelBackground());

    // Require opaque to ensure the color is shown
    mainPanel.setOpaque(true);

    // Deregister any previous references
    if (headerView != null) {
      CoreServices.uiEventBus.unregister(headerView);
      CoreServices.uiEventBus.unregister(sidebarView);
      CoreServices.uiEventBus.unregister(detailView);
      CoreServices.uiEventBus.unregister(footerView);
    }

    // Create supporting views (rebuild every time for language support)
    headerView = new HeaderView();
    sidebarView = new SidebarView();
    detailView = new DetailView();
    footerView = new FooterView();

    // Create a splitter pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    // Set the divider width (3 is about right for a clean look)
    splitPane.setDividerSize(3);

    int sidebarWidth = MultiBitUI.SIDEBAR_LHS_PREF_WIDTH;
    try {
      sidebarWidth = Integer.valueOf(Configurations.currentConfiguration.getApplication().getSidebarWidth());
    } catch (NumberFormatException e) {
      log.warn("Sidebar width configuration is not a number - using default");
    }

    if (Languages.isLeftToRight()) {
      splitPane.setLeftComponent(sidebarView.getContentPanel());
      splitPane.setRightComponent(detailView.getContentPanel());
      splitPane.setDividerLocation(sidebarWidth);
    } else {
      splitPane.setLeftComponent(detailView.getContentPanel());
      splitPane.setRightComponent(sidebarView.getContentPanel());
      splitPane.setDividerLocation(Panels.applicationFrame.getWidth() - sidebarWidth);
    }

    // Sets the colouring for divider and borders
    splitPane.setBackground(Themes.currentTheme.text());
    splitPane.setBorder(BorderFactory.createMatteBorder(
      1, 0, 1, 0,
      Themes.currentTheme.text()
    ));

    splitPane.applyComponentOrientation(Languages.currentComponentOrientation());

    splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
      new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent pce) {

          // Keep the current configuration up to date
          Configurations.currentConfiguration.getApplication().setSidebarWidth(String.valueOf(pce.getNewValue()));

        }
      }
    );

    // Add the supporting panels
    mainPanel.add(headerView.getContentPanel(), "growx,shrink,wrap"); // Ensure header size remains fixed
    mainPanel.add(splitPane, "grow,push,wrap");
    mainPanel.add(footerView.getContentPanel(), "growx,shrink"); // Ensure footer size remains fixed

    return mainPanel;
  }

  /**
   * @param show True if the exiting welcome wizard should be shown during the next refresh
   */
  public void setShowExitingWelcomeWizard(boolean show) {

    showExitingWelcomeWizard = show;

  }

  /**
   * @param show True if the exiting password wizard should be shown during the next refresh
   */
  public void setShowExitingPasswordWizard(boolean show) {

    showExitingPasswordWizard = show;

  }

  /**
   * Attempt to get focus to the sidebar
   */
  public void sidebarRequestFocus() {

    sidebarView.requestFocus();
  }

  /**
   * Update the sidebar wallet name tree node
   *
   * @param walletName The wallet name
   */
  public void sidebarWalletName(String walletName) {

    sidebarView.updateWalletTreeNode(walletName);

  }

  /**
   * Update the detail view to reflect the new wallet
   */
  public void detailViewAfterWalletOpened() {

    detailView.afterWalletOpened();
  }

}