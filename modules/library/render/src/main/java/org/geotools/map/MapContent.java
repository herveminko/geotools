/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2010-2011, Open Source Geospatial Foundation (OSGeo)
 *    
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.map;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.event.MapBoundsEvent;
import org.geotools.map.event.MapBoundsListener;
import org.geotools.map.event.MapLayerEvent;
import org.geotools.map.event.MapLayerListEvent;
import org.geotools.map.event.MapLayerListListener;
import org.geotools.map.event.MapLayerListener;
import org.geotools.referencing.CRS;
import org.geotools.util.logging.Logging;

import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

/**
 * Store the contents of a map for display primarily as a list of Layers.
 * <p>
 * This object is similar to early drafts of the OGC Open Web Service Context specification.
 *
 * @author Jody Garnett
 *
 * @source $URL: http://svn.osgeo.org/geotools/branches/2.7.x/build/maven/javadoc/../../../modules/library/render/src/main/java/org/geotools/map/MapContent.java $
 *         http://svn.osgeo.org/geotools/trunk/modules/library/render/src/main/java/org/geotools
 *         /map/MapContext.java $
 * @version $Id: Map.java 35310 2010-04-30 10:32:15Z jive $
 */
public class MapContent {

    /** The logger for the map module. */
    static protected final Logger LOGGER = Logging.getLogger("org.geotools.map");
    /** List of Layers to be rendered */
    private final LayerList layerList;
    /** MapLayerListListeners to be notified in the event of change */
    private CopyOnWriteArrayList<MapLayerListListener> mapListeners;
    /** Map used to hold application specific information */
    private HashMap<String, Object> userData;
    /** Map title */
    private String title;
    /** PropertyListener list used for notifications */
    private CopyOnWriteArrayList<PropertyChangeListener> propertyListeners;
    /**
     * Viewport for map rendering.
     * 
     * While the map maintains one viewport internally to better reflect a map context document you
     * are free to maintain a seperate viewport; or indeed construct many viewports representing
     * tiles to be renderered.
     */
    protected MapViewport viewport;
    /** Listener used to watch individual layers and report changes to MapLayerListListeners */
    private MapLayerListener layerListener;
    private final ReadWriteLock monitor;

    /**
     * Creates a new map content.
     */
    public MapContent() {
        layerList = new LayerList();
        monitor = new ReentrantReadWriteLock();
    }

    /**
     * Checks that dispose has been called; producing a warning if needed.
     */
    @Override
    protected void finalize() throws Throwable {
        if (this.layerList != null) {
            if (!this.layerList.isEmpty()) {
                LOGGER.severe("Call MapContent dispose() to prevent memory leaks");
            }
        }
        super.finalize();
    }

    /**
     * Clean up any listeners or cached state associated with this MapContent.
     * <p>
     * Please note that open connections (FeatureSources and GridCoverage readers) are
     * the responsibility of your application and are not cleaned up by this method.
     */
    public void dispose() {
        for (Layer layer : layerList) {
            if (layer != null) {
                // Layer.dispose will inform listeners of the impending
                // disposal, then remove listeners from this layer
                layer.dispose();
            }
        }
        layerList.clear();


        if (this.layerListener != null) {
            this.layerListener = null;
        }

        if (this.propertyListeners != null) {
            this.propertyListeners.clear();
            this.propertyListeners = null;
        }
        this.title = null;
        if (this.userData != null) {
            // remove property listeners prior to removing userData
            this.userData.clear();
            this.userData = null;
        }
    }

    public MapContent(MapContext context) {
        this();

        for (MapLayer mapLayer : context.getLayers()) {
            layerList.add(mapLayer.toLayer());
        }
        if (context.getTitle() != null) {
            setTitle(context.getTitle());
        }
        if (context.getAbstract() != null) {
            getUserData().put("abstract", context.getAbstract());
        }
        if (context.getContactInformation() != null) {
            getUserData().put("contact", context.getContactInformation());
        }
        if (context.getKeywords() != null) {
            getUserData().put("keywords", context.getKeywords());
        }
        if (context.getAreaOfInterest() != null) {
            getViewport().setBounds(context.getAreaOfInterest());
        }
    }

    @Deprecated
    public MapContent(CoordinateReferenceSystem crs) {
        this();
        getViewport().setCoordinateReferenceSystem(crs);
    }

    @Deprecated
    public MapContent(MapLayer[] array) {
        this(array, null);
    }

    @Deprecated
    public MapContent(MapLayer[] array, CoordinateReferenceSystem crs) {
        this(array, "Untitled", "", "", null, crs);
    }

    @Deprecated
    public MapContent(MapLayer[] array, String title, String contextAbstract, String contactInformation,
            String[] keywords) {
        this(array, title, contextAbstract, contactInformation, keywords, null);
    }

    @Deprecated
    public MapContent(MapLayer[] array, String title, String contextAbstract, String contactInformation,
            String[] keywords, final CoordinateReferenceSystem crs) {
        this();

        if (array != null) {
            for (MapLayer mapLayer : array) {

                if (mapLayer == null) {
                    continue;
                }
                Layer layer = mapLayer.toLayer();
                layerList.add(layer);
            }
        }
        if (title != null) {
            setTitle(title);
        }
        if (contextAbstract != null) {
            getUserData().put("abstract", contextAbstract);
        }
        if (contactInformation != null) {
            getUserData().put("contact", contactInformation);
        }
        if (keywords != null) {
            getUserData().put("keywords", keywords);
        }
        if (crs != null) {
            getViewport().setCoordinateReferenceSystem(crs);
        }
    }

    /**
     * Register interest in receiving a {@link LayerListEvent}. A <code>LayerListEvent</code> is
     * sent if a layer is added or removed, but not if the data within a layer changes.
     * 
     * @param listener
     *            The object to notify when Layers have changed.
     */
    public void addMapLayerListListener(MapLayerListListener listener) {
        monitor.writeLock().lock();
        try {
            if (mapListeners == null) {
                mapListeners = new CopyOnWriteArrayList<MapLayerListListener>();
            }
            boolean added = mapListeners.addIfAbsent(listener);
            if (added && mapListeners.size() == 1) {
                listenToMapLayers(true);
            }
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Listen to the map layers; passing any events on to our own mapListListeners.
     * <p>
     * This method only has an effect if we have any actuall mapListListeners.
     * 
     * @param listen
     *            True to connect to all the layers and listen to events
     */
    protected void listenToMapLayers(boolean listen) {
        monitor.writeLock().lock();
        try {
            if (mapListeners == null || mapListeners.isEmpty()) {
                return; // not worth listening nobody is interested
            }
            if (layerListener == null) {
                layerListener = new MapLayerListener() {

                    public void layerShown(MapLayerEvent event) {
                        Layer layer = (Layer) event.getSource();
                        int index = layerList.indexOf(layer);
                        fireLayerEvent(layer, index, event);
                    }

                    public void layerSelected(MapLayerEvent event) {
                        Layer layer = (Layer) event.getSource();
                        int index = layerList.indexOf(layer);
                        fireLayerEvent(layer, index, event);
                    }

                    public void layerHidden(MapLayerEvent event) {
                        Layer layer = (Layer) event.getSource();
                        int index = layerList.indexOf(layer);
                        fireLayerEvent(layer, index, event);
                    }

                    public void layerDeselected(MapLayerEvent event) {
                        Layer layer = (Layer) event.getSource();
                        int index = layerList.indexOf(layer);
                        fireLayerEvent(layer, index, event);
                    }

                    public void layerChanged(MapLayerEvent event) {
                        Layer layer = (Layer) event.getSource();
                        int index = layerList.indexOf(layer);
                        fireLayerEvent(layer, index, event);
                    }
                };
            }
            if (listen) {
                for (Layer layer : layerList) {
                    layer.addMapLayerListener(layerListener);
                }
            } else {
                for (Layer layer : layerList) {
                    layer.removeMapLayerListener(layerListener);
                }
            }
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Remove interest in receiving {@link LayerListEvent}.
     * 
     * @param listener
     *            The object to stop sending <code>LayerListEvent</code>s.
     */
    public void removeMapLayerListListener(MapLayerListListener listener) {
        monitor.writeLock().lock();
        try {
            if (mapListeners != null) {
                mapListeners.remove(listener);
            }
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Add a new layer (if not already present).
     * <p>
     * In an interactive setting this will trigger a {@link LayerListEvent}
     * 
     * @param layer
     * @return true if the layer was added
     */
    public boolean addLayer(Layer layer) {
        monitor.writeLock().lock();
        try {
            return layerList.addIfAbsent(layer);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Adds all layers from the input collection that are not already present
     * in this map content.
     * 
     * @param layers layers to add (may be {@code null} or empty)
     * @return the number of layers added
     */
    public int addLayers(Collection<? extends Layer> layers) {
        monitor.writeLock().lock();
        try {
            if (layers == null || layers.isEmpty()) {
                return 0;
            }

            return layerList.addAllAbsent(layers);

        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Remove a layer, if present, and trigger a {@link LayerListEvent}.
     * 
     * @param layer
     *            a MapLayer that will be added.
     * 
     * @return true if the layer has been removed
     */
    public boolean removeLayer(Layer layer) {
        monitor.writeLock().lock();
        try {
            return layerList.remove(layer);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Moves a layer from a position to another. Will fire a MapLayerListEvent
     * 
     * @param sourcePosition
     *            the layer current position
     * @param destPosition
     *            the layer new position
     */
    public void moveLayer(int sourcePosition, int destPosition) {
        monitor.writeLock().lock();
        try {
            Layer destLayer = layerList.get(destPosition);
            Layer sourceLayer = layerList.get(sourcePosition);
            layerList.set(destPosition, sourceLayer);
            layerList.set(sourcePosition, destLayer);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Direct access to the layer list; contents arranged by zorder.
     * <p>
     * Please note this list is live and modifications made to the list will trigger
     * {@link LayerListEvent}
     * 
     * @return Direct access to layers
     */
    public CopyOnWriteArrayList<Layer> layers() {
        monitor.readLock().lock();
        try {
            return layerList;
        } finally {
            monitor.readLock().unlock();
        }
    }

    protected void fireLayerAdded(Layer element, int fromIndex, int toIndex) {
        monitor.readLock().lock();
        try {
            if (mapListeners == null) {
                return;
            }
            MapLayerListEvent event = new MapLayerListEvent(this, element, fromIndex, toIndex);
            for (MapLayerListListener mapLayerListListener : mapListeners) {
                try {
                    mapLayerListListener.layerAdded(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.logp(Level.FINE, mapLayerListListener.getClass().getName(),
                                "layerAdded", t.getLocalizedMessage(), t);
                    }
                }
            }
        } finally {
            monitor.readLock().unlock();
        }
    }

    protected void fireLayerRemoved(Layer element, int fromIndex, int toIndex) {
        monitor.readLock().lock();
        try {
            if (mapListeners == null) {
                return;
            }
            MapLayerListEvent event = new MapLayerListEvent(this, element, fromIndex, toIndex);
            for (MapLayerListListener mapLayerListListener : mapListeners) {
                try {
                    mapLayerListListener.layerRemoved(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.logp(Level.FINE, mapLayerListListener.getClass().getName(),
                                "layerAdded", t.getLocalizedMessage(), t);
                    }
                }
            }
        } finally {
            monitor.readLock().unlock();
        }
    }

    protected void fireLayerMoved(Layer element, int toIndex) {
        monitor.readLock().lock();
        try {
            if (mapListeners == null) {
                return;
            }
            MapLayerListEvent event = new MapLayerListEvent(this, element, toIndex);
            for (MapLayerListListener mapLayerListListener : mapListeners) {
                try {
                    mapLayerListListener.layerMoved(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.logp(Level.FINE, mapLayerListListener.getClass().getName(),
                                "layerMoved", t.getLocalizedMessage(), t);
                    }
                }
            }
        } finally {
            monitor.readLock().unlock();
        }
    }

    protected void fireLayerEvent(Layer element, int index, MapLayerEvent layerEvent) {
        monitor.readLock().lock();
        try {
            if (mapListeners == null) {
                return;
            }
            MapLayerListEvent mapEvent = new MapLayerListEvent(this, element, index, layerEvent);
            for (MapLayerListListener mapLayerListListener : mapListeners) {
                try {
                    mapLayerListListener.layerChanged(mapEvent);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.logp(Level.FINE, mapLayerListListener.getClass().getName(),
                                "layerAdded", t.getLocalizedMessage(), t);
                    }
                }
            }
        } finally {
            monitor.readLock().unlock();
        }
    }

    /**
     * Get the bounding box of all the layers in this Map. If all the layers cannot determine the
     * bounding box in the speed required for each layer, then null is returned. The bounds will be
     * expressed in the Map coordinate system.
     * 
     * @return The bounding box of the features or null if unknown and too expensive for the method
     *         to calculate.
     * 
     * @throws IOException
     *             if an IOException occurs while accessing the FeatureSource bounds
     */
    public ReferencedEnvelope getMaxBounds() {
        monitor.readLock().lock();
        try {
            CoordinateReferenceSystem mapCrs = null;
            if (viewport != null) {
                mapCrs = viewport.getCoordianteReferenceSystem();
            }
            ReferencedEnvelope maxBounds = null;

            for (Layer layer : layerList) {
                if (layer == null) {
                    continue;
                }
                try {
                    ReferencedEnvelope layerBounds = layer.getBounds();
                    if (layerBounds == null || layerBounds.isEmpty() || layerBounds.isNull()) {
                        continue;
                    }
                    if (mapCrs == null) {
                        // crs for the map is not defined; let us start with the first CRS we see then!
                        maxBounds = new ReferencedEnvelope(layerBounds);
                        mapCrs = layerBounds.getCoordinateReferenceSystem();
                        continue;
                    }
                    ReferencedEnvelope normalized;
                    if (CRS.equalsIgnoreMetadata(mapCrs, layerBounds.getCoordinateReferenceSystem())) {
                        normalized = layerBounds;
                    } else {
                        try {
                            normalized = layerBounds.transform(mapCrs, true);
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Unable to transform: {0}", e);
                            continue;
                        }
                    }
                    if (maxBounds == null) {
                        maxBounds = normalized;
                    } else {
                        maxBounds.expandToInclude(normalized);
                    }
                } catch (Throwable eek) {
                    LOGGER.warning("Unable to determine bounds of " + layer + ":" + eek);
                }
            }
            if (maxBounds == null && mapCrs != null) {
                maxBounds = new ReferencedEnvelope(mapCrs);
            }
            return maxBounds;

        } finally {
            monitor.readLock().unlock();
        }
    }

    //
    // Viewport Information
    //
    /**
     * Viewport describing the area visiable on screen.
     * <p>
     * Applications may create multiple viewports (perhaps to render tiles of content); the viewport
     * recorded here is intended for interactive applications where it is helpful to have a single
     * viewport representing what the user is seeing on screen.
     */
    public MapViewport getViewport() {
        monitor.readLock().lock();
        try {
            if (viewport == null) {
                viewport = new MapViewport();
            }
            return viewport;
        } finally {
            monitor.readLock().unlock();
        }
    }

    /**
     * Sets the viewport for this map content and returns the previously
     * set one (which may be {@code null}). The {@code viewport} argument may
     * be {@code null}, in which case a subsequent to {@linkplain #getViewport()}
     * will return a new instance with default settings.
     * 
     * @param viewport the new viewport
     */
    public void setViewport(MapViewport viewport) {
        monitor.writeLock().lock();
        try {
            this.viewport = viewport;
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Register interest in receiving {@link MapBoundsEvent}s.
     * 
     * @param listener
     *            The object to notify when the area of interest has changed.
     */
    public void addMapBoundsListener(MapBoundsListener listener) {
        monitor.writeLock().lock();
        try {
            getViewport().addMapBoundsListener(listener);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Remove interest in receiving a {@link BoundingBoxEvent}s.
     * 
     * @param listener
     *            The object to stop sending change events.
     */
    public void removeMapBoundsListener(MapBoundsListener listener) {
        monitor.writeLock().lock();
        try {
            getViewport().removeMapBoundsListener(listener);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * The extent of the map currently (sometimes called the map "viewport".
     * <p>
     * Note Well: The bounds should match your screen aspect ratio (or the map will appear
     * squashed). Please note this only covers spatial extent; you may wish to use the user data map
     * to record the current viewport time or elevation.
     */
    ReferencedEnvelope getBounds() {
        monitor.readLock().lock();
        try {
            return getViewport().getBounds();
        } finally {
            monitor.readLock().unlock();
        }
    }

    /**
     * The coordinate reference system used for rendering the map.
     * <p>
     * The coordinate reference system used for rendering is often considered to be the "world"
     * coordinate reference system; this is distinct from the coordinate reference system used for
     * each layer (which is often data dependent).
     * </p>
     * 
     * @return coordinate reference system used for rendering the map.
     */
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        monitor.readLock().lock();
        try {
            return getViewport().getCoordinateReferenceSystem();
        } finally {
            monitor.readLock().unlock();
        }
    }

    /**
     * Set the <code>CoordinateReferenceSystem</code> for this map's internal viewport.
     * 
     * @param crs
     * @throws FactoryException
     * @throws TransformException
     */
    void setCoordinateReferenceSystem(CoordinateReferenceSystem crs) {
        monitor.writeLock().lock();
        try {
            getViewport().setCoordinateReferenceSystem(crs);
        } finally {
            monitor.writeLock().unlock();
        }
    }

    //
    // Properties
    //
    /**
     * Registers PropertyChangeListener to receive events.
     * 
     * @param listener
     *            The listener to register.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        monitor.writeLock().lock();
        try {
            if (propertyListeners == null) {
                propertyListeners = new CopyOnWriteArrayList<java.beans.PropertyChangeListener>();
            }
            if (!propertyListeners.contains(listener)) {
                propertyListeners.add(listener);
            }

        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Removes PropertyChangeListener from the list of listeners.
     * 
     * @param listener
     *            The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        monitor.writeLock().lock();
        try {
            if (propertyListeners != null) {
                propertyListeners.remove(listener);
            }

        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * As an example it can be used to record contact details, map abstract, keywords and so forth
     * for an OGC "Open Web Service Context" document.
     * <p>
     * Modifications to the userData will result in a propertyChange event.
     * </p>
     * 
     * @return
     */
    public java.util.Map<String, Object> getUserData() {
        monitor.writeLock().lock();
        try {
            if (userData == null) {
                userData = new HashMap<String, Object>() {

                    private static final long serialVersionUID = 8011733882551971475L;

                    public Object put(String key, Object value) {
                        Object old = super.put(key, value);
                        fireProperty(key, old, value);
                        return old;
                    }

                    public Object remove(Object key) {
                        Object old = super.remove(key);
                        fireProperty((String) key, old, null);
                        return old;
                    }

                    @Override
                    public void putAll(java.util.Map<? extends String, ? extends Object> m) {
                        super.putAll(m);
                        fireProperty("userData", null, null);
                    }

                    @Override
                    public void clear() {
                        super.clear();
                        fireProperty("userData", null, null);
                    }
                };
            }
            return this.userData;
        } finally {
            monitor.writeLock().unlock();
        }
    }

    /**
     * Get the title, returns an empty string if it has not been set yet.
     * 
     * @return the title, or an empty string if it has not been set.
     */
    public String getTitle() {
        monitor.readLock().lock();
        try {
            return title;
        } finally {
            monitor.readLock().unlock();
        }
    }

    /**
     * Set the title of this context.
     * 
     * @param title
     *            the title.
     */
    public void setTitle(String title) {
        monitor.writeLock().lock();
        try {
            String old = this.title;
            this.title = title;
            fireProperty("title", old, title);

        } finally {
            monitor.writeLock().unlock();
        }
    }

    protected void fireProperty(String propertyName, Object old, Object value) {
        monitor.readLock().lock();
        try {
            if (propertyListeners == null) {
                return;
            }
            PropertyChangeEvent event = new PropertyChangeEvent(this, "propertyName", old, value);
            for (PropertyChangeListener propertyChangeListener : propertyListeners) {
                try {
                    propertyChangeListener.propertyChange(event);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINER)) {
                        LOGGER.logp(Level.FINE, propertyChangeListener.getClass().getName(),
                                "propertyChange", t.getLocalizedMessage(), t);
                    }
                }
            }

        } finally {
            monitor.readLock().unlock();
        }
    }

    private class LayerList extends CopyOnWriteArrayList<Layer> {

        private static final long serialVersionUID = 8011733882551971475L;

        /**
         * Adds a layer at the specified position in the list. Does
         * nothing if the layer is already present.
         * 
         * @param index position for the layer
         * @param element the layer to add
         */
        @Override
        public void add(int index, Layer element) {
            if (!contains(element)) {
                super.add(index, element);
                if (layerListener != null) {
                    element.addMapLayerListener(layerListener);
                }
                fireLayerAdded(element, index, index);
            }
        }

        /**
         * Adds a layer if it is not already present. Equivalent to
         * {@linkplain #addIfAbsent(Layer)}.
         * 
         * @param element the layer to add
         * @return {@code true} if the layer was added; {@code false} if
         *     it was already present in the list
         */
        @Override
        public boolean add(Layer element) {
            return addIfAbsent(element);
        }

        /**
         * Adds all layers from the input collection that are not already
         * present in this list. Equivalent to {@code addAllAbsent(layers) > 0}.
         * 
         * @param layers candidate layers to add
         * @return {@code true} is any layers were added; {@code false} otherwise
         */
        @Override
        public boolean addAll(Collection<? extends Layer> layers) {
            return addAllAbsent(layers) > 0;
        }

        /**
         * Adds all layers from the input collection that are not already
         * present in this list, with the first added layer taking position
         * {@code index}.
         * 
         * @param index position of the first added layer in the list
         * @param layers candidate layers to add
         * 
         * @return {@code true} if any layers were added; {@code false} otherwise
         */
        @Override
        public boolean addAll(int index, Collection<? extends Layer> layers) {
            boolean added = false;
            int pos = index;

            for (Layer layer : layers) {
                if (!contains(layer)) {
                    add(pos, layer);
                    if (layerListener != null) {
                        layer.addMapLayerListener(layerListener);
                    }
                    added = true;
                    pos++;
                }
            }

            if (added) {
                fireLayerAdded(null, index, size() - 1);
            }

            return added;
        }

        /**
         * Adds all layers from the input collection that are not already
         * present in this list.
         * 
         * @param layers candidate layers to add
         * @return the number of layers added
         */
        @Override
        public int addAllAbsent(Collection<? extends Layer> layers) {
            int start = size();
            int added = super.addAllAbsent(layers);
            if (layerListener != null) {
                for (int i = start; i < size(); i++) {
                    get(i).addMapLayerListener(layerListener);
                }
            }
            fireLayerAdded(null, start, size() - 1);
            return added;
        }

        /**
         * Adds a layer if it is not already present.
         * 
         * @param element the layer to add
         * @return {@code true} if the layer was added; {@code false} if
         *     it was already present in the list
         */
        @Override
        public boolean addIfAbsent(Layer element) {
            boolean added = super.addIfAbsent(element);
            if (added) {
                if (layerListener != null) {
                    element.addMapLayerListener(layerListener);
                }
                fireLayerAdded(element, size() - 1, size() - 1);
            }
            return added;
        }

        /**
         * Removes all layers from this list.
         */
        @Override
        public void clear() {
            for (Layer element : this) {
                if (layerListener != null) {
                    element.removeMapLayerListener(layerListener);
                }
                element.dispose();
            }
            super.clear();
            fireLayerRemoved(null, -1, -1);
        }

        /**
         * Removes the layer at position {@code index} from this list.
         * 
         * @param index the position of the layer to be removed
         * @return the layer
         */
        @Override
        public Layer remove(int index) {
            Layer removed = super.remove(index);
            fireLayerRemoved(removed, index, index);
            if (layerListener != null) {
                removed.removeMapLayerListener(layerListener);
            }
            removed.dispose();
            return removed;
        }

        /**
         * Removes the specified element, which much be a Layer, from the list
         * if present.
         * 
         * @param element the element to remove
         * @return {@code true} if removed; {@code false} if not present in the list
         */
        @Override
        public boolean remove(Object element) {
            boolean removed = super.remove(element);
            if (removed) {
                fireLayerRemoved((Layer) element, -1, -1);
                if (element instanceof Layer) {
                    Layer layer = (Layer) element;
                    if (layerListener != null) {
                        layer.removeMapLayerListener(layerListener);
                    }
                    layer.dispose();
                }
            }
            return removed;
        }

        /**
         * Removes all layers in the input collection from this list, if present.
         * 
         * @param layers the candidate layers to remove
         * @return {@code true} if any layers were removed; {@code false} otherwise
         */
        @Override
        public boolean removeAll(Collection<?> layers) {
            for (Object obj : layers) {
                Layer element = (Layer) obj;
                if (!contains(element)) {
                    continue;
                }
                if (layerListener != null) {
                    element.removeMapLayerListener(layerListener);
                }
                element.dispose();
            }
            boolean removed = super.removeAll(layers);
            fireLayerRemoved(null, 0, size() - 1);
            return removed;
        }

        /**
         * Removes any layers from this list that are not contained in the
         * input collection.
         * 
         * @param layers the layers which should not be removed
         * @return {@code true} if any layers were removed; {@code false} otherwise
         */
        @Override
        public boolean retainAll(Collection<?> layers) {
            for (Object obj : layers) {
                Layer element = (Layer) obj;
                if (contains(element)) {
                    continue;
                }
                if (layerListener != null) {
                    element.removeMapLayerListener(layerListener);
                }
                element.dispose();
            }
            boolean removed = super.retainAll(layers);
            fireLayerRemoved(null, 0, size() - 1);
            return removed;
        }

        /**
         * TODO: make this method issue removed and add events and 
         * set layer listener
         * 
         * @param index
         * @param element
         * @return 
         */
        @Override
        public Layer set(int index, Layer element) {
            Layer set = super.set(index, element);
            fireLayerMoved(element, index);
            return set;
        }
    }
}
