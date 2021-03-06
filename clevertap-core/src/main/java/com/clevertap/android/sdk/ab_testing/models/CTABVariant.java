package com.clevertap.android.sdk.ab_testing.models;

import androidx.annotation.NonNull;
import com.clevertap.android.sdk.ImageCache;
import com.clevertap.android.sdk.Logger;
import com.clevertap.android.sdk.Utils;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class CTABVariant {

    final public class CTVariantAction {

        private String activityName;

        private JSONObject change;

        private String name;

        CTVariantAction(String name, String activityName, JSONObject change) {
            this.name = name;
            this.activityName = activityName;
            this.change = change;
        }

        public String getActivityName() {
            return activityName;
        }

        public JSONObject getChange() {
            return change;
        }

        public String getName() {
            return name;
        }
    }

    private ArrayList<CTVariantAction> actions = new ArrayList<>();

    private final Object actionsLock = new Object();

    private String experimentId;

    private String id;

    private ArrayList<String> imageUrls;

    private String variantId;

    private JSONArray vars;

    private int version;

    public static CTABVariant initWithJSON(JSONObject json) {
        try {
            String experimentId = json.optString("exp_id", "0");
            String variantId = json.optString("var_id", "0");
            int version = json.optInt("version", 0);
            final JSONArray actions = json.optJSONArray("actions");
            final JSONArray vars = json.optJSONArray("vars");
            CTABVariant variant = new CTABVariant(experimentId, variantId, version, actions, vars);
            Logger.v("Created CTABVariant:  " + variant.toString());
            return variant;
        } catch (Throwable t) {
            Logger.v("Error creating variant", t);
            return null;
        }
    }

    private CTABVariant(String experimentId, String variantId, int version, JSONArray actions, JSONArray vars) {
        this.experimentId = experimentId;
        this.variantId = variantId;
        this.id = variantId + ":" + experimentId;
        this.version = version;
        imageUrls = new ArrayList<>();
        addActions(actions);
        this.vars = vars == null ? new JSONArray() : vars;
    }

    public void addActions(JSONArray actions) {
        synchronized (actionsLock) {
            if (actions == null || actions.length() <= 0) {
                return;
            }
            final int actionsLength = actions.length();
            try {
                for (int j = 0; j < actionsLength; j++) {
                    final JSONObject change = actions.getJSONObject(j);
                    if (change == null) {
                        continue;
                    }
                    final String targetActivity = Utils.optionalStringKey(change, "target_activity");
                    final String name = change.getString("name");
                    boolean exists = false;
                    CTVariantAction existingAction = null;
                    for (CTVariantAction action : this.actions) {
                        if (action.getName().equals(name)) {
                            exists = true;
                            existingAction = action;
                            break;
                        }
                    }
                    if (exists) {
                        this.actions.remove(existingAction);
                    }
                    final CTVariantAction action = new CTVariantAction(name, targetActivity, change);
                    this.actions.add(action);
                }
            } catch (Throwable t) {
                Logger.v("Error adding variant actions", t);
            }
        }
    }

    public void addImageUrls(List<String> urls) {
        if (urls == null) {
            return;
        }
        this.imageUrls.addAll(urls);
    }

    public void cleanup() {
        for (String url : imageUrls) {
            ImageCache.removeBitmap(url, true);
        }
    }

    public void clearActions() {
        synchronized (actionsLock) {
            actions.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CTABVariant) {
            CTABVariant other = (CTABVariant) o;
            return this.getId().equals(other.getId()) && this.getVersion() == other.getVersion();
        }
        return false;
    }

    public ArrayList<CTVariantAction> getActions() {
        synchronized (actionsLock) {
            return this.actions;
        }
    }

    public String getId() {
        return id;
    }

    public JSONArray getVars() {
        return vars;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        return this.getId().hashCode();
    }

    public void removeActionsByName(JSONArray names) {
        if (names == null || names.length() <= 0) {
            return;
        }
        synchronized (actionsLock) {
            ArrayList<String> _names = new ArrayList<>();
            for (int i = 0; i < names.length(); i++) {
                try {
                    _names.add(names.getString(i));
                } catch (Throwable t) {
                    // no-op
                }
            }
            ArrayList<CTVariantAction> newActions = new ArrayList<>();
            for (CTVariantAction action : actions) {
                if (!_names.contains(action.getName())) {
                    newActions.add(action);
                }
            }
            this.actions = newActions;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "< id: " + getId() + ", version: " + getVersion() + ", actions count: " + actions.size()
                + ", vars count: " + getVars().length() + " >";
    }

    @SuppressWarnings("unused")
    String getExperimentId() {
        return experimentId;
    }

    @SuppressWarnings("unused")
    String getVariantId() {
        return variantId;
    }
}
