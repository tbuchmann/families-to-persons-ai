package de.university.hof.genai.f2p;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import Families.FamiliesFactory;
import Families.FamiliesPackage;
import Families.Family;
import Families.FamilyMember;
import Families.FamilyRegister;
import Persons.Female;
import Persons.Male;
import Persons.Person;
import Persons.PersonRegister;
import Persons.PersonsFactory;
import Persons.PersonsPackage;

public class IncrementalModelTransformer {
	
	private Resource familiesResource;
	private Resource personsResource;
	private boolean preferParent = true;
	private boolean preferExisting = true;
	private boolean isTransforming = false;
	
	public IncrementalModelTransformer(Resource source, Resource target) {
		familiesResource = source;
		personsResource = target;
	}
	
	public void configure(boolean preferParent, boolean preferExisting) {
		this.preferParent = preferParent;
		this.preferExisting = preferExisting;
	}

    private Map<FamilyMember, Person> familyMemberToPersonMap = new HashMap<>();
    private Map<Person, FamilyMember> personToFamilyMemberMap = new HashMap<>();
    
	public static void main(String[] args) {
	    // Load Families and Persons models
	    ResourceSet resourceSet = new ResourceSetImpl();
	    resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
	    resourceSet.getPackageRegistry().put(FamiliesPackage.eNS_URI, FamiliesPackage.eINSTANCE);
	    resourceSet.getPackageRegistry().put(PersonsPackage.eNS_URI, PersonsPackage.eINSTANCE);
	
	    Resource familiesResource = resourceSet.getResource(URI.createFileURI("path/to/Families.ecore"), true);
	    Resource personsResource = resourceSet.getResource(URI.createFileURI("path/to/Persons.ecore"), true);
	
	    FamilyRegister familyRegister = (FamilyRegister) familiesResource.getContents().get(0);
	    PersonRegister personRegister = (PersonRegister) personsResource.getContents().get(0);
	    
	    IncrementalModelTransformer t = new IncrementalModelTransformer(familiesResource, personsResource);
	
	    // Add listeners for changes
	    t.addFamilyRegisterListener(familyRegister, personRegister);
	    t.addPersonRegisterListener(personRegister, familyRegister);
	
	    // Initial transformation
	    t.transformFamiliesToPersons(familyRegister, personRegister);
	    t.transformPersonsToFamilies(personRegister, familyRegister, true, true);
	
	    // Save the transformed models
	    t.saveModel(familiesResource, "path/to/TransformedFamilies.xmi");
	    t.saveModel(personsResource, "path/to/TransformedPersons.xmi");
	}
	
	public void addFamilyRegisterListener(FamilyRegister familyRegister, PersonRegister personRegister) {
        Adapter familyAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                if (!isTransforming) {
                    handleFamilyRegisterChange(notification, personRegister);
                }
            }
        };
        familyRegister.eAdapters().add(familyAdapter);
        
        for (Family family : familyRegister.getFamilies()) {
            addFamilyListener(family);
        }
    }
	
	public void addFamilyListener(Family family) {
        Adapter familyMemberAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                if (notification.getNotifier() instanceof FamilyMember) {
                    FamilyMember familyMember = (FamilyMember) notification.getNotifier();
                    updatePersonFromFamilyMember(familyMember);
                }
                
            }
        };

        if (family.getFather() != null) {
            family.getFather().eAdapters().add(familyMemberAdapter);
        }
        if (family.getMother() != null) {
            family.getMother().eAdapters().add(familyMemberAdapter);
        }
        for (FamilyMember son : family.getSons()) {
            son.eAdapters().add(familyMemberAdapter);
        }
        for (FamilyMember daughter : family.getDaughters()) {
            daughter.eAdapters().add(familyMemberAdapter);
        }
    }

    public void addPersonRegisterListener(PersonRegister personRegister, FamilyRegister familyRegister) {
        Adapter personAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                if (!isTransforming) {
                    handlePersonRegisterChange(notification, familyRegister);
                }
            }
        };
        personRegister.eAdapters().add(personAdapter);
    }

    public void handleFamilyRegisterChange(Notification notification, PersonRegister personRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Family) {
                Family family = (Family) notification.getNewValue();
                transformFamilyToPersons(family, personRegister);
                addFamilyListener(family);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Family) {
                Family family = (Family) notification.getOldValue();
                removeFamilyPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }

    public void handlePersonRegisterChange(Notification notification, FamilyRegister familyRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Person) {
                Person person = (Person) notification.getNewValue();
                transformPersonToFamilyMember(person, familyRegister, preferExisting, preferParent);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Person) {
                Person person = (Person) notification.getOldValue();
                removePersonFamilyMember(person, familyRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }
    
    public void updatePersonFromFamilyMember(FamilyMember familyMember) {
        Person person = familyMemberToPersonMap.get(familyMember);
        if (person != null) {
            Family family = findFamilyByMember(familyMember);
            if (family == null) {
                // Remove the person if the family member is no longer part of a family
                personToFamilyMemberMap.remove(person);
                familyMemberToPersonMap.remove(familyMember);
                person.eResource().getContents().remove(person);
            } else {
                person.setName(family.getName() + ", " + familyMember.getName());
            }
        }
    }
    
    public Family findFamilyByMember(FamilyMember familyMember) {
        for (Family family : ((FamilyRegister) familiesResource.getContents().get(0)).getFamilies()) {
            if (family.getFather() == familyMember || family.getMother() == familyMember ||
                family.getSons().contains(familyMember) || family.getDaughters().contains(familyMember)) {
                return family;
            }
        }
        return null;
    }

    public void transformFamiliesToPersons(FamilyRegister familyRegister, PersonRegister personRegister) {
        isTransforming = true;
        try {
            for (Family family : familyRegister.getFamilies()) {
                transformFamilyToPersons(family, personRegister);
            }
        } finally {
            isTransforming = false;
        }
    }

    public void transformFamilyToPersons(Family family, PersonRegister personRegister) {
        List<Person> newPersons = new ArrayList<>();

        // Transform father
        if (family.getFather() != null && !familyMemberToPersonMap.containsKey(family.getFather())) {
            Male father = PersonsFactory.eINSTANCE.createMale();
            father.setName(family.getName() + ", " + family.getFather().getName());
            newPersons.add(father);
            familyMemberToPersonMap.put(family.getFather(), father);
            personToFamilyMemberMap.put(father, family.getFather());
        }
        // Transform mother
        if (family.getMother() != null && !familyMemberToPersonMap.containsKey(family.getMother())) {
            Female mother = PersonsFactory.eINSTANCE.createFemale();
            mother.setName(family.getName() + ", " + family.getMother().getName());
            newPersons.add(mother);
            familyMemberToPersonMap.put(family.getMother(), mother);
            personToFamilyMemberMap.put(mother, family.getMother());
        }
        // Transform sons
        for (FamilyMember son : family.getSons()) {
            if (!familyMemberToPersonMap.containsKey(son)) {
                Male maleSon = PersonsFactory.eINSTANCE.createMale();
                maleSon.setName(family.getName() + ", " + son.getName());
                newPersons.add(maleSon);
                familyMemberToPersonMap.put(son, maleSon);
                personToFamilyMemberMap.put(maleSon, son);
            }
        }
        // Transform daughters
        for (FamilyMember daughter : family.getDaughters()) {
            if (!familyMemberToPersonMap.containsKey(daughter)) {
                Female femaleDaughter = PersonsFactory.eINSTANCE.createFemale();
                femaleDaughter.setName(family.getName() + ", " + daughter.getName());
                newPersons.add(femaleDaughter);
                familyMemberToPersonMap.put(daughter, femaleDaughter);
                personToFamilyMemberMap.put(femaleDaughter, daughter);
            }
        }

        // Add new persons after iteration
        personRegister.getPersons().addAll(newPersons);
    }

    public void removeFamilyPersons(Family family, PersonRegister personRegister) {
        if (family.getFather() != null) {
            Person person = familyMemberToPersonMap.remove(family.getFather());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        if (family.getMother() != null) {
            Person person = familyMemberToPersonMap.remove(family.getMother());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember son : family.getSons()) {
            Person person = familyMemberToPersonMap.remove(son);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember daughter : family.getDaughters()) {
            Person person = familyMemberToPersonMap.remove(daughter);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
    }

    public void transformPersonsToFamilies(PersonRegister personRegister, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        isTransforming = true;
        try {
            for (Person person : personRegister.getPersons()) {
                transformPersonToFamilyMember(person, familyRegister, addToExistingFamilies, preferParentRole);
            }
        } finally {
            isTransforming = false;
        }
    }

    public void transformPersonToFamilyMember(Person person, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        if (personToFamilyMemberMap.containsKey(person)) {
            return; // Skip transformation if already mapped
        }

        String[] nameParts = person.getName().split(", ");
        if (nameParts.length != 2) {
            return; // Skip malformed names
        }
        String familyName = nameParts[0];
        String personName = nameParts[1];

        Family family = findOrCreateFamily(familyRegister, familyName, addToExistingFamilies);

        FamilyMember familyMember = FamiliesFactory.eINSTANCE.createFamilyMember();
        familyMember.setName(personName);

        if (preferParentRole) {
            if (person instanceof Male) {
                if (family.getFather() == null) {
                    family.setFather(familyMember);
                } else {
                    family.getSons().add(familyMember);
                }
            } else if (person instanceof Female) {
                if (family.getMother() == null) {
                    family.setMother(familyMember);
                } else {
                    family.getDaughters().add(familyMember);
                }
            }
        } else {
            if (person instanceof Male) {
                family.getSons().add(familyMember);
            } else if (person instanceof Female) {
                family.getDaughters().add(familyMember);
            }
        }
        personToFamilyMemberMap.put(person, familyMember);
        familyMemberToPersonMap.put(familyMember, person);
    }

    public void removePersonFamilyMember(Person person, FamilyRegister familyRegister) {
        FamilyMember familyMember = personToFamilyMemberMap.remove(person);
        familyMemberToPersonMap.remove(familyMember);
        if (familyMember == null) {
            return;
        }

        for (Family family : familyRegister.getFamilies()) {
            if (family.getFather() == familyMember) {
                family.setFather(null);
            } else if (family.getMother() == familyMember) {
                family.setMother(null);
            } else if (family.getSons().contains(familyMember)) {
                family.getSons().remove(familyMember);
            } else if (family.getDaughters().contains(familyMember)) {
                family.getDaughters().remove(familyMember);
            }
        }
    }

    public Family findOrCreateFamily(FamilyRegister familyRegister, String familyName, boolean addToExistingFamilies) {
        if (addToExistingFamilies) {
            for (Family existingFamily : familyRegister.getFamilies()) {
                if (existingFamily.getName().equals(familyName)) {
                    return existingFamily;
                }
            }
        }
        Family newFamily = FamiliesFactory.eINSTANCE.createFamily();
        newFamily.setName(familyName);
        familyRegister.getFamilies().add(newFamily);
        return newFamily;
    }

    public void saveModel(Resource resource, String filePath) {
        try {
            resource.setURI(URI.createFileURI(filePath));
            resource.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
 /* Variante ohne isTransforming flag   
    public void addFamilyRegisterListener(FamilyRegister familyRegister, PersonRegister personRegister) {
        Adapter familyRegisterAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handleFamilyRegisterChange(notification, personRegister);
            }
        };
        familyRegister.eAdapters().add(familyRegisterAdapter);

        for (Family family : familyRegister.getFamilies()) {
            addFamilyListener(family, personRegister);
        }
    }

    public void addFamilyListener(Family family, PersonRegister personRegister) {
        Adapter familyAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handleFamilyChange(notification, personRegister);
            }
        };
        family.eAdapters().add(familyAdapter);
    }

    public void addPersonRegisterListener(PersonRegister personRegister, FamilyRegister familyRegister) {
        Adapter personRegisterAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handlePersonRegisterChange(notification, familyRegister);
            }
        };
        personRegister.eAdapters().add(personRegisterAdapter);
    }

    public void handleFamilyRegisterChange(Notification notification, PersonRegister personRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Family) {
                Family family = (Family) notification.getNewValue();
                addFamilyListener(family, personRegister);
                transformFamilyToPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Family) {
                Family family = (Family) notification.getOldValue();
                removeFamilyPersons(family, personRegister);
            }
        }
    }

    public void handleFamilyChange(Notification notification, PersonRegister personRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof FamilyMember) {
                Family family = (Family) notification.getNotifier();
                FamilyMember familyMember = (FamilyMember) notification.getNewValue();
                transformFamilyMemberToPerson(family, familyMember, personRegister);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof FamilyMember) {
                FamilyMember familyMember = (FamilyMember) notification.getOldValue();
                removeFamilyMemberPerson(familyMember, personRegister);
            }
        }
    }
    
    public void removeFamilyMemberPerson(FamilyMember familyMember, PersonRegister personRegister) {
        Person person = familyMemberToPersonMap.remove(familyMember);
        if (person != null) {
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
    }

    public void handlePersonRegisterChange(Notification notification, FamilyRegister familyRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Person) {
                Person person = (Person) notification.getNewValue();
                transformPersonToFamilyMember(person, familyRegister, true, true);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Person) {
                Person person = (Person) notification.getOldValue();
                removePersonFamilyMember(person, familyRegister);
            }
        }
    }

    public void transformFamiliesToPersons(FamilyRegister familyRegister, PersonRegister personRegister) {
        for (Family family : familyRegister.getFamilies()) {
            transformFamilyToPersons(family, personRegister);
        }
    }

    public void transformFamilyToPersons(Family family, PersonRegister personRegister) {
    	List<Person> newPersons = new ArrayList<>();

        // Transform father
        if (family.getFather() != null && !familyMemberToPersonMap.containsKey(family.getFather())) {
            Male father = PersonsFactory.eINSTANCE.createMale();
            father.setName(family.getName() + ", " + family.getFather().getName());
            newPersons.add(father);
            familyMemberToPersonMap.put(family.getFather(), father);
            personToFamilyMemberMap.put(father, family.getFather());
        }
        // Transform mother
        if (family.getMother() != null && !familyMemberToPersonMap.containsKey(family.getMother())) {
            Female mother = PersonsFactory.eINSTANCE.createFemale();
            mother.setName(family.getName() + ", " + family.getMother().getName());
            newPersons.add(mother);
            familyMemberToPersonMap.put(family.getMother(), mother);
            personToFamilyMemberMap.put(mother, family.getMother());
        }
        // Transform sons
        for (FamilyMember son : family.getSons()) {
            if (!familyMemberToPersonMap.containsKey(son)) {
                Male maleSon = PersonsFactory.eINSTANCE.createMale();
                maleSon.setName(family.getName() + ", " + son.getName());
                newPersons.add(maleSon);
                familyMemberToPersonMap.put(son, maleSon);
                personToFamilyMemberMap.put(maleSon, son);
            }
        }
        // Transform daughters
        for (FamilyMember daughter : family.getDaughters()) {
            if (!familyMemberToPersonMap.containsKey(daughter)) {
                Female femaleDaughter = PersonsFactory.eINSTANCE.createFemale();
                femaleDaughter.setName(family.getName() + ", " + daughter.getName());
                newPersons.add(femaleDaughter);
                familyMemberToPersonMap.put(daughter, femaleDaughter);
                personToFamilyMemberMap.put(femaleDaughter, daughter);
            }
        }

        // Add new persons after iteration
        personRegister.getPersons().addAll(newPersons);
    }

    public void transformFamilyMemberToPerson(Family family, FamilyMember familyMember, PersonRegister personRegister) {
        Person person = null;
        if (family.getFather() == familyMember) {
            person = PersonsFactory.eINSTANCE.createMale();
        } else if (family.getMother() == familyMember) {
            person = PersonsFactory.eINSTANCE.createFemale();
        } else if (family.getSons().contains(familyMember)) {
            person = PersonsFactory.eINSTANCE.createMale();
        } else if (family.getDaughters().contains(familyMember)) {
            person = PersonsFactory.eINSTANCE.createFemale();
        }

        if (person != null) {
            person.setName(family.getName() + ", " + familyMember.getName());
            personRegister.getPersons().add(person);
            familyMemberToPersonMap.put(familyMember, person);
            personToFamilyMemberMap.put(person, familyMember);
        }
    }

    public void removeFamilyPersons(Family family, PersonRegister personRegister) {
        if (family.getFather() != null) {
            Person person = familyMemberToPersonMap.remove(family.getFather());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        if (family.getMother() != null) {
            Person person = familyMemberToPersonMap.remove(family.getMother());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember son : family.getSons()) {
            Person person = familyMemberToPersonMap.remove(son);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember daughter : family.getDaughters()) {
            Person person = familyMemberToPersonMap.remove(daughter);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
    }

    public void transformPersonsToFamilies(PersonRegister personRegister, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        for (Person person : personRegister.getPersons()) {
            transformPersonToFamilyMember(person, familyRegister, addToExistingFamilies, preferParentRole);
        }
    }

    public void transformPersonToFamilyMember(Person person, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
    	if (personToFamilyMemberMap.containsKey(person)) {
            return; // Skip transformation if already mapped
        }

        String[] nameParts = person.getName().split(", ");
        if (nameParts.length != 2) {
            return; // Skip malformed names
        }
        String familyName = nameParts[0];
        String personName = nameParts[1];

        Family family = findOrCreateFamily(familyRegister, familyName, addToExistingFamilies);

        FamilyMember familyMember = FamiliesFactory.eINSTANCE.createFamilyMember();
        familyMember.setName(personName);

        if (preferParentRole) {
            if (person instanceof Male) {
                if (family.getFather() == null) {
                    family.setFather(familyMember);
                } else {
                    family.getSons().add(familyMember);
                }
            } else if (person instanceof Female) {
                if (family.getMother() == null) {
                    family.setMother(familyMember);
                } else {
                    family.getDaughters().add(familyMember);
                }
            }
        } else {
            if (person instanceof Male) {
                family.getSons().add(familyMember);
            } else if (person instanceof Female) {
                family.getDaughters().add(familyMember);
            }
        }
        personToFamilyMemberMap.put(person, familyMember);
        familyMemberToPersonMap.put(familyMember, person);
    }

    public void removePersonFamilyMember(Person person, FamilyRegister familyRegister) {
        FamilyMember familyMember = personToFamilyMemberMap.remove(person);
        familyMemberToPersonMap.remove(familyMember);
        if (familyMember == null) {
            return;
        }

        for (Family family : familyRegister.getFamilies()) {
            if (family.getFather() == familyMember) {
                family.setFather(null);
            } else if (family.getMother() == familyMember) {
                family.setMother(null);
            } else if (family.getSons().contains(familyMember)) {
                family.getSons().remove(familyMember);
            } else if (family.getDaughters().contains(familyMember)) {
                family.getDaughters().remove(familyMember);
            }
        }
    }

    public Family findOrCreateFamily(FamilyRegister familyRegister, String familyName, boolean addToExistingFamilies) {
        if (addToExistingFamilies) {
            for (Family existingFamily : familyRegister.getFamilies()) {
                if (existingFamily.getName().equals(familyName)) {
                    return existingFamily;
                }
            }
        }
        Family newFamily = FamiliesFactory.eINSTANCE.createFamily();
        newFamily.setName(familyName);
        familyRegister.getFamilies().add(newFamily);
        return newFamily;
    }

    public void saveModel(Resource resource, String filePath) {
        try {
            resource.setURI(URI.createFileURI(filePath));
            resource.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */

/* Version 2 ohne Family Listener
    public static void main(String[] args) {
        // Load Families and Persons models
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(FamiliesPackage.eNS_URI, FamiliesPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(PersonsPackage.eNS_URI, PersonsPackage.eINSTANCE);

        Resource familiesResource = resourceSet.getResource(URI.createFileURI("path/to/Families.ecore"), true);
        Resource personsResource = resourceSet.getResource(URI.createFileURI("path/to/Persons.ecore"), true);
        
        IncrementalModelTransformer t = new IncrementalModelTransformer(familiesResource, personsResource);

        FamilyRegister familyRegister = (FamilyRegister) familiesResource.getContents().get(0);
        PersonRegister personRegister = (PersonRegister) personsResource.getContents().get(0);

        // Add listeners for changes
        t.addFamilyRegisterListener(familyRegister, personRegister);
        t.addPersonRegisterListener(personRegister, familyRegister);

        // Initial transformation
        t.transformFamiliesToPersons(familyRegister, personRegister);
        t.transformPersonsToFamilies(personRegister, familyRegister, true, true);

        // Save the transformed models
        t.saveModel(familiesResource, "path/to/TransformedFamilies.xmi");
        t.saveModel(personsResource, "path/to/TransformedPersons.xmi");
    }
    

    public void addFamilyRegisterListener(FamilyRegister familyRegister, PersonRegister personRegister) {
        Adapter familyAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handleFamilyRegisterChange(notification, personRegister);
            }
        };
        familyRegister.eAdapters().add(familyAdapter);
    }

    public void addPersonRegisterListener(PersonRegister personRegister, FamilyRegister familyRegister) {
        Adapter personAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handlePersonRegisterChange(notification, familyRegister);
            }
        };
        personRegister.eAdapters().add(personAdapter);
    }

    public void handleFamilyRegisterChange(Notification notification, PersonRegister personRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Family) {
                Family family = (Family) notification.getNewValue();
                transformFamilyToPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Family) {
                Family family = (Family) notification.getOldValue();
                removeFamilyPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }

    public void handlePersonRegisterChange(Notification notification, FamilyRegister familyRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Person) {
                Person person = (Person) notification.getNewValue();
                transformPersonToFamilyMember(person, familyRegister, preferExisting, preferParent);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Person) {
                Person person = (Person) notification.getOldValue();
                removePersonFamilyMember(person, familyRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }

    public void transformFamiliesToPersons(FamilyRegister familyRegister, PersonRegister personRegister) {
        for (Family family : familyRegister.getFamilies()) {
            transformFamilyToPersons(family, personRegister);
        }
    }

    public void transformFamilyToPersons(Family family, PersonRegister personRegister) {
        List<Person> newPersons = new ArrayList<>();

        // Transform father
        if (family.getFather() != null) {
            Male father = PersonsFactory.eINSTANCE.createMale();
            father.setName(family.getName() + ", " + family.getFather().getName());
            newPersons.add(father);
            familyMemberToPersonMap.put(family.getFather(), father);
            personToFamilyMemberMap.put(father, family.getFather());
        }
        // Transform mother
        if (family.getMother() != null) {
            Female mother = PersonsFactory.eINSTANCE.createFemale();
            mother.setName(family.getName() + ", " + family.getMother().getName());
            newPersons.add(mother);
            familyMemberToPersonMap.put(family.getMother(), mother);
            personToFamilyMemberMap.put(mother, family.getMother());
        }
        // Transform sons
        for (FamilyMember son : family.getSons()) {
            Male maleSon = PersonsFactory.eINSTANCE.createMale();
            maleSon.setName(family.getName() + ", " + son.getName());
            newPersons.add(maleSon);
            familyMemberToPersonMap.put(son, maleSon);
            personToFamilyMemberMap.put(maleSon, son);
        }
        // Transform daughters
        for (FamilyMember daughter : family.getDaughters()) {
            Female femaleDaughter = PersonsFactory.eINSTANCE.createFemale();
            femaleDaughter.setName(family.getName() + ", " + daughter.getName());
            newPersons.add(femaleDaughter);
            familyMemberToPersonMap.put(daughter, femaleDaughter);
            personToFamilyMemberMap.put(femaleDaughter, daughter);
        }

        // Add new persons after iteration
        personRegister.getPersons().addAll(newPersons);
    }

    public void removeFamilyPersons(Family family, PersonRegister personRegister) {
        if (family.getFather() != null) {
            Person person = familyMemberToPersonMap.remove(family.getFather());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        if (family.getMother() != null) {
            Person person = familyMemberToPersonMap.remove(family.getMother());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember son : family.getSons()) {
            Person person = familyMemberToPersonMap.remove(son);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember daughter : family.getDaughters()) {
            Person person = familyMemberToPersonMap.remove(daughter);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
    }

    public void transformPersonsToFamilies(PersonRegister personRegister, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        for (Person person : personRegister.getPersons()) {
            transformPersonToFamilyMember(person, familyRegister, addToExistingFamilies, preferParentRole);
        }
    }

    public void transformPersonToFamilyMember(Person person, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        String[] nameParts = person.getName().split(", ");
        if (nameParts.length != 2) {
            return; // Skip malformed names
        }
        String familyName = nameParts[0];
        String personName = nameParts[1];

        Family family = findOrCreateFamily(familyRegister, familyName, addToExistingFamilies);

        FamilyMember familyMember = FamiliesFactory.eINSTANCE.createFamilyMember();
        familyMember.setName(personName);

        if (preferParentRole) {
            if (person instanceof Male) {
                if (family.getFather() == null) {
                    family.setFather(familyMember);
                } else {
                    family.getSons().add(familyMember);
                }
            } else if (person instanceof Female) {
                if (family.getMother() == null) {
                    family.setMother(familyMember);
                } else {
                    family.getDaughters().add(familyMember);
                }
            }
        } else {
            if (person instanceof Male) {
                family.getSons().add(familyMember);
            } else if (person instanceof Female) {
                family.getDaughters().add(familyMember);
            }
        }
        personToFamilyMemberMap.put(person, familyMember);
        familyMemberToPersonMap.put(familyMember, person);
    }

    public void removePersonFamilyMember(Person person, FamilyRegister familyRegister) {
        FamilyMember familyMember = personToFamilyMemberMap.remove(person);
        familyMemberToPersonMap.remove(familyMember);
        if (familyMember == null) {
            return;
        }

        for (Family family : familyRegister.getFamilies()) {
            if (family.getFather() == familyMember) {
                family.setFather(null);
            } else if (family.getMother() == familyMember) {
                family.setMother(null);
            } else if (family.getSons().contains(familyMember)) {
                family.getSons().remove(familyMember);
            } else if (family.getDaughters().contains(familyMember)) {
                family.getDaughters().remove(familyMember);
            }
        }
    }

    public Family findOrCreateFamily(FamilyRegister familyRegister, String familyName, boolean addToExistingFamilies) {
        if (addToExistingFamilies) {
            for (Family existingFamily : familyRegister.getFamilies()) {
                if (existingFamily.getName().equals(familyName)) {
                    return existingFamily;
                }
            }
        }
        Family newFamily = FamiliesFactory.eINSTANCE.createFamily();
        newFamily.setName(familyName);
        familyRegister.getFamilies().add(newFamily);
        return newFamily;
    }

    public void saveModel(Resource resource, String filePath) {
        try {
            resource.setURI(URI.createFileURI(filePath));
            resource.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
*/    
    /*
     * Variante 1 -> Concurrent ModificationException

    public void addFamilyRegisterListener(FamilyRegister familyRegister, PersonRegister personRegister) {
        Adapter familyAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handleFamilyRegisterChange(notification, personRegister);
            }
        };
        familyRegister.eAdapters().add(familyAdapter);
    }

    public void addPersonRegisterListener(PersonRegister personRegister, FamilyRegister familyRegister) {
        Adapter personAdapter = new AdapterImpl() {
            @Override
            public void notifyChanged(Notification notification) {
                handlePersonRegisterChange(notification, familyRegister);
            }
        };
        personRegister.eAdapters().add(personAdapter);
    }

    public void handleFamilyRegisterChange(Notification notification, PersonRegister personRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Family) {
                Family family = (Family) notification.getNewValue();
                transformFamilyToPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Family) {
                Family family = (Family) notification.getOldValue();
                removeFamilyPersons(family, personRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }

    public void handlePersonRegisterChange(Notification notification, FamilyRegister familyRegister) {
        if (notification.getEventType() == Notification.ADD) {
            if (notification.getNewValue() instanceof Person) {
                Person person = (Person) notification.getNewValue();
                transformPersonToFamilyMember(person, familyRegister, preferExisting, preferParent);
            }
        } else if (notification.getEventType() == Notification.REMOVE) {
            if (notification.getOldValue() instanceof Person) {
                Person person = (Person) notification.getOldValue();
                removePersonFamilyMember(person, familyRegister);
            }
        } else if (notification.getEventType() == Notification.SET) {
            // Handle attribute modifications
            // Add further logic if needed
        }
    }

    public void transformFamiliesToPersons(FamilyRegister familyRegister, PersonRegister personRegister) {
        for (Family family : familyRegister.getFamilies()) {
            transformFamilyToPersons(family, personRegister);
        }
    }

    public void transformFamilyToPersons(Family family, PersonRegister personRegister) {
        // Transform father
        if (family.getFather() != null) {
            Male father = PersonsFactory.eINSTANCE.createMale();
            father.setName(family.getName() + ", " + family.getFather().getName());
            personRegister.getPersons().add(father);
            familyMemberToPersonMap.put(family.getFather(), father);
            personToFamilyMemberMap.put(father, family.getFather());
        }
        // Transform mother
        if (family.getMother() != null) {
            Female mother = PersonsFactory.eINSTANCE.createFemale();
            mother.setName(family.getName() + ", " + family.getMother().getName());
            personRegister.getPersons().add(mother);
            familyMemberToPersonMap.put(family.getMother(), mother);
            personToFamilyMemberMap.put(mother, family.getMother());
        }
        // Transform sons
        for (FamilyMember son : family.getSons()) {
            Male maleSon = PersonsFactory.eINSTANCE.createMale();
            maleSon.setName(family.getName() + ", " + son.getName());
            personRegister.getPersons().add(maleSon);
            familyMemberToPersonMap.put(son, maleSon);
            personToFamilyMemberMap.put(maleSon, son);
        }
        // Transform daughters
        for (FamilyMember daughter : family.getDaughters()) {
            Female femaleDaughter = PersonsFactory.eINSTANCE.createFemale();
            femaleDaughter.setName(family.getName() + ", " + daughter.getName());
            personRegister.getPersons().add(femaleDaughter);
            familyMemberToPersonMap.put(daughter, femaleDaughter);
            personToFamilyMemberMap.put(femaleDaughter, daughter);
        }
    }

    public void removeFamilyPersons(Family family, PersonRegister personRegister) {
        if (family.getFather() != null) {
            Person person = familyMemberToPersonMap.remove(family.getFather());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        if (family.getMother() != null) {
            Person person = familyMemberToPersonMap.remove(family.getMother());
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember son : family.getSons()) {
            Person person = familyMemberToPersonMap.remove(son);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
        for (FamilyMember daughter : family.getDaughters()) {
            Person person = familyMemberToPersonMap.remove(daughter);
            personToFamilyMemberMap.remove(person);
            personRegister.getPersons().remove(person);
        }
    }

    public void transformPersonsToFamilies(PersonRegister personRegister, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        for (Person person : personRegister.getPersons()) {
            transformPersonToFamilyMember(person, familyRegister, addToExistingFamilies, preferParentRole);
        }
    }

    public void transformPersonToFamilyMember(Person person, FamilyRegister familyRegister, 
    		boolean addToExistingFamilies, boolean preferParentRole) {
        String[] nameParts = person.getName().split(", ");
        if (nameParts.length != 2) {
            return; // Skip malformed names
        }
        String familyName = nameParts[0];
        String personName = nameParts[1];

        Family family = findOrCreateFamily(familyRegister, familyName, addToExistingFamilies);

        FamilyMember familyMember = FamiliesFactory.eINSTANCE.createFamilyMember();
        familyMember.setName(personName);

        if (preferParentRole) {
            if (person instanceof Male) {
                if (family.getFather() == null) {
                    family.setFather(familyMember);
                } else {
                    family.getSons().add(familyMember);
                }
            } else if (person instanceof Female) {
                if (family.getMother() == null) {
                    family.setMother(familyMember);
                } else {
                    family.getDaughters().add(familyMember);
                }
            }
        } else {
            if (person instanceof Male) {
                family.getSons().add(familyMember);
            } else if (person instanceof Female) {
                family.getDaughters().add(familyMember);
            }
        }
        personToFamilyMemberMap.put(person, familyMember);
        familyMemberToPersonMap.put(familyMember, person);
    }

    public void removePersonFamilyMember(Person person, FamilyRegister familyRegister) {
        FamilyMember familyMember = personToFamilyMemberMap.remove(person);
        familyMemberToPersonMap.remove(familyMember);
        if (familyMember == null) {
            return;
        }

        for (Family family : familyRegister.getFamilies()) {
            if (family.getFather() == familyMember) {
                family.setFather(null);
            } else if (family.getMother() == familyMember) {
                family.setMother(null);
            } else if (family.getSons().contains(familyMember)) {
                family.getSons().remove(familyMember);
            } else if (family.getDaughters().contains(familyMember)) {
                family.getDaughters().remove(familyMember);
            }
        }
    }

    public Family findOrCreateFamily(FamilyRegister familyRegister, String familyName, boolean addToExistingFamilies) {
        if (addToExistingFamilies) {
            for (Family existingFamily : familyRegister.getFamilies()) {
                if (existingFamily.getName().equals(familyName)) {
                    return existingFamily;
                }
            }
        }
        Family newFamily = FamiliesFactory.eINSTANCE.createFamily();
        newFamily.setName(familyName);
        familyRegister.getFamilies().add(newFamily);
        return newFamily;
    }

    public void saveModel(Resource resource, String filePath) {
        try {
            resource.setURI(URI.createFileURI(filePath));
            resource.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */
}
