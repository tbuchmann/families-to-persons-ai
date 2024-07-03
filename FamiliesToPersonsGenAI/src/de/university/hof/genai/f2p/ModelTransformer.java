package de.university.hof.genai.f2p;

import java.io.IOException;
import java.util.Arrays;

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

public class ModelTransformer {
	private Resource familiesResource;
	private Resource personsResource;
	
	public ModelTransformer(Resource source, Resource target) {
		familiesResource = source;
		personsResource = target;
	}

    public static void main(String[] args) {
        // Load Families and Persons models
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new XMIResourceFactoryImpl());
        resourceSet.getPackageRegistry().put(FamiliesPackage.eNS_URI, FamiliesPackage.eINSTANCE);
        resourceSet.getPackageRegistry().put(PersonsPackage.eNS_URI, PersonsPackage.eINSTANCE);

        Resource familiesResource = resourceSet.getResource(URI.createFileURI("path/to/Families.ecore"), true);
        Resource personsResource = resourceSet.getResource(URI.createFileURI("path/to/Persons.ecore"), true);
        
        ModelTransformer t = new ModelTransformer(familiesResource, personsResource);

        FamilyRegister familyRegister = (FamilyRegister) familiesResource.getContents().get(0);
        PersonRegister personRegister = (PersonRegister) personsResource.getContents().get(0);

        // Transform Families to Persons
        t.transformFamiliesToPersons(familyRegister, personRegister);

        // Save the transformed model
        t.saveModel(personsResource, "path/to/TransformedPersons.xmi");
    }

    public void transformFamiliesToPersons(FamilyRegister familyRegister, PersonRegister personRegister) {
        for (Family family : familyRegister.getFamilies()) {
            // Transform father
            if (family.getFather() != null) {
                Male father = PersonsFactory.eINSTANCE.createMale();
                father.setName(family.getName() + ", " + family.getFather().getName());
                personRegister.getPersons().add(father);
            }
            // Transform mother
            if (family.getMother() != null) {
                Female mother = PersonsFactory.eINSTANCE.createFemale();
                mother.setName(family.getName() + ", " + family.getMother().getName());
                personRegister.getPersons().add(mother);
            }
            // Transform sons
            for (FamilyMember son : family.getSons()) {
                Male maleSon = PersonsFactory.eINSTANCE.createMale();
                maleSon.setName(family.getName() + ", " + son.getName());
                personRegister.getPersons().add(maleSon);
            }
            // Transform daughters
            for (FamilyMember daughter : family.getDaughters()) {
                Female femaleDaughter = PersonsFactory.eINSTANCE.createFemale();
                femaleDaughter.setName(family.getName() + ", " + daughter.getName());
                personRegister.getPersons().add(femaleDaughter);
            }
        }
    }

    public void transformPersonsToFamilies(PersonRegister personRegister, FamilyRegister familyRegister, boolean addToExistingFamilies, boolean preferParentRole) {
        for (Person person : personRegister.getPersons()) {
            String[] nameParts = person.getName().split(", ");
            if (nameParts.length != 2) {
                continue; // Skip malformed names
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
        }
    }

    private Family findOrCreateFamily(FamilyRegister familyRegister, String familyName, boolean addToExistingFamilies) {
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
}
