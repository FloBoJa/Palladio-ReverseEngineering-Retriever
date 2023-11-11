package org.palladiosimulator.somox.analyzer.rules.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServiceConfiguration<T extends Service> {
    private final String selectedServicesKey;
    private final String serviceConfigKeyPrefix;
    private final Map<String, Map<String, String>> serviceConfigs;
    private final Map<String, T> services;
    private final Set<T> manuallySelectedServices;
    private final Map<T, Set<Service>> selectedDependencies;
    private final Set<ServiceConfiguration<? extends Service>> dependencyProviders;

    public ServiceConfiguration(ServiceCollection<T> serviceCollection, String selectedServicesKey,
            String serviceConfigKeyPrefix) {
        this.selectedServicesKey = selectedServicesKey;
        this.serviceConfigKeyPrefix = serviceConfigKeyPrefix;
        this.serviceConfigs = new HashMap<>();
        this.services = new HashMap<>();
        for (T service : serviceCollection.getServices()) {
        	this.services.put(service.getID(), service);
        	Map<String, String> initializedConfig = new HashMap<>();
        	for (String key : service.getConfigurationKeys()) {
        		initializedConfig.put(key, "");
        	}
        	this.serviceConfigs.put(service.getID(), initializedConfig);
        }
        this.manuallySelectedServices = new HashSet<>();
        this.selectedDependencies = new HashMap<>();
        this.dependencyProviders = new HashSet<>();
        this.dependencyProviders.add(this);
    }
    
    public void addDependencyProvider(ServiceConfiguration<? extends Service> dependencyProvider) {
    	dependencyProviders.add(dependencyProvider);
    }

    @SuppressWarnings("unchecked")
    public void applyAttributeMap(Map<String, Object> attributeMap) {
        Set<String> serviceIds = (Set<String>) attributeMap.get(selectedServicesKey);
        for (Map.Entry<String, T> serviceEntry : services.entrySet()) {
            String serviceId = serviceEntry.getKey();
            T service = serviceEntry.getValue();
            if (attributeMap.get(serviceConfigKeyPrefix + serviceId) != null) {
                serviceConfigs.put(serviceId,
                        (Map<String, String>) attributeMap.get(serviceConfigKeyPrefix + serviceId));
            }
            if ((serviceIds != null) && serviceIds.contains(service.getID())) {
                this.select(service);
            }
        }

    }

    public String getConfig(String serviceId, String key) {
        Map<String, String> config = serviceConfigs.get(serviceId);
        if (config == null) {
            return null;
        }
        return config.get(key);
    }

    public Map<String, String> getWholeConfig(String serviceId) {
        return Collections.unmodifiableMap(serviceConfigs.get(serviceId));
    }

    public void setConfig(String serviceId, String key, String value) {
        Map<String, String> config = serviceConfigs.get(serviceId);
        if (config == null) {
            config = new HashMap<>();
            serviceConfigs.put(serviceId, config);
        }
        config.put(key, value);
    }

    public void select(T service) {
        manuallySelectedServices.add(service);
        for (ServiceConfiguration<? extends Service> dependencyProvider : dependencyProviders) {
            dependencyProvider.selectDependenciesOf(service);
        }
    }

    public void deselect(T service) {
        manuallySelectedServices.remove(service);
        for (ServiceConfiguration<? extends Service> dependencyProvider : dependencyProviders) {
            dependencyProvider.deselectDependenciesOf(service);
        }
    }

    public boolean isManuallySelected(T service) {
        return manuallySelectedServices.contains(service);
    }

    public Set<T> getSelected() {
    	Set<T> selectedServices = new HashSet<>(manuallySelectedServices);
    	selectedServices.addAll(selectedDependencies.keySet());
        return Collections.unmodifiableSet(selectedServices);
    }
    
    public Collection<T> getAvailable() {
    	return Collections.unmodifiableCollection(services.values());
    }
    
    public void selectDependenciesOf(Service service) {
    	for (String dependencyID : service.getRequiredServices()) {
    		if (!services.containsKey(dependencyID)) {
    			continue;
    		}
    		T dependency = services.get(dependencyID);
    		addDependingService(dependency, service);
    	}
    }
    
    private void addDependingService(T dependency, Service service) {
    	if (selectedDependencies.containsKey(dependency)) {
    		Set<Service> dependingServices = selectedDependencies.get(dependency);
    		dependingServices.add(service);
    	} else {
    		Set<Service> dependingServices = new HashSet<>();
    		dependingServices.add(service);
    		selectedDependencies.put(dependency, dependingServices);
    		for (ServiceConfiguration<? extends Service> dependencyProvider : dependencyProviders) {
    			dependencyProvider.selectDependenciesOf(dependency);
    		}
    	}
    }
    
    public void deselectDependenciesOf(Service service) {
    	for (String dependencyID : service.getRequiredServices()) {
    		if (!services.containsKey(dependencyID)) {
    			continue;
    		}
    		T dependency = services.get(dependencyID);
    		removeDependingService(dependency, service);
    	}
    }
    
    private void removeDependingService(T dependency, Service service) {
    	if (!selectedDependencies.containsKey(dependency)) {
    		return;
    	}
    	Set<Service> dependingServices = selectedDependencies.get(dependency);
    	dependingServices.remove(service);
    	// Remove not needed dependencies.
    	if (dependingServices.isEmpty()) {
    		selectedDependencies.remove(dependency);
    	}
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> result = new HashMap<>();
        for (String serviceId : serviceConfigs.keySet()) {
            result.put(serviceConfigKeyPrefix + serviceId, serviceConfigs.get(serviceId));
        }
        result.put(selectedServicesKey, serializeServices(manuallySelectedServices));
        return result;
    }

    private static Set<String> serializeServices(Iterable<? extends Service> services) {
        Set<String> serviceIds = new HashSet<>();
        for (Service service : services) {
            serviceIds.add(service.getID());
        }
        return serviceIds;
    }
}
