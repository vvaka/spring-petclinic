/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.petclinic.owner;

import io.micrometer.core.annotation.Timed;
import io.pivotal.petclinic.visit.VisitRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.util.*;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */
@Controller
@Timed("petclinic.owner")
@Component
class OwnerController {

    private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final VisitRepository visitRepository;

    private Random randomGen = new Random();
    private int FIND_TIME_MS = 250;

    public OwnerController(OwnerRepository ownerRepository, PetRepository petRepository, VisitRepository visitRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.visitRepository = visitRepository;
    }

    @InitBinder
    public void setAllowedFields(WebDataBinder dataBinder) {
        dataBinder.setDisallowedFields("id");
    }

    @GetMapping("/owners/new")
    public String initCreationForm(Map<String, Object> model) {
        Owner owner = new Owner();
        model.put("owner", owner);
        return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
    }

    @PostMapping("/owners/new")
    public String processCreationForm(@Valid Owner owner, BindingResult result) {
        if (result.hasErrors()) {
            return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
        } else {
            owner.setId(UUID.randomUUID().toString());
            this.ownerRepository.save(owner);
            return "redirect:/owners/" + owner.getId();
        }
    }

    @GetMapping("/owners/find")
    public String initFindForm(Map<String, Object> model) {
        model.put("owner", new Owner());
        return "owners/findOwners";
    }

    @GetMapping("/owners")
    @Timed("petclinic.owners.find.time")
    public String processFindForm(Owner owner, BindingResult result, Map<String, Object> model) {

        // allow parameterless GET request for /ownerRepository to return all records
        if (owner.getLastName() == null) {
            owner.setLastName(""); // empty string signifies broadest possible search
        }

        int delayInMilliseconds = 0;
        try {
            delayInMilliseconds = FIND_TIME_MS + randomGen.nextInt(100);
            Thread.sleep(delayInMilliseconds);
        } catch (InterruptedException e) {
        }

        // find ownerRepository by last name
        Collection<Owner> results = this.ownerRepository.findByLastNameOrderByLastName(owner.getLastName() + "%");
        if (results.isEmpty()) {
            // no ownerRepository found
            result.rejectValue("lastName", "notFound", "not found");
            return "owners/findOwners";
        } else if (results.size() == 1) {
            // 1 owner found
            owner = results.iterator().next();
            return "redirect:/owners/" + owner.getId();
        } else {
            // multiple ownerRepository found

            List<DisplayOwner> dOwners = new ArrayList<>();
            results.forEach(owner1 -> {
                dOwners.add(getDisplayOwner(owner1));
            });

            model.put("selections", dOwners);
            return "owners/ownersList";
        }
    }

    @GetMapping("/owners/{ownerId}/edit")
    public String initUpdateOwnerForm(@PathVariable("ownerId") String ownerId, Model model) {
        Owner owner = this.ownerRepository.findById(ownerId).get();
        model.addAttribute(owner);
        return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
    }

    @PostMapping("/owners/{ownerId}/edit")
    public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") String ownerId) {
        if (result.hasErrors()) {
            return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
        } else {
            owner.setId(ownerId);
            this.ownerRepository.save(owner);
            return "redirect:/owners/{ownerId}";
        }
    }

    /**
     * Custom handler for displaying an owner.
     *
     * @param ownerId the ID of the owner to display
     * @return a ModelMap with the model attributes for the view
     */
    @GetMapping("/owners/{ownerId}")
    public ModelAndView showOwner(@PathVariable("ownerId") String ownerId) {
        ModelAndView mav = new ModelAndView("owners/ownerDetails");
        mav.addObject("owner", getDisplayOwner(this.ownerRepository.findById(ownerId).get()));
        return mav;
    }

    private DisplayOwner getDisplayOwner(Owner owner) {
        DisplayOwner dOwner = new DisplayOwner(owner);
        List<Pet> pets = this.petRepository.findByOwnerIdOrderByName(owner.getId());
        pets.forEach(pet -> {
            DisplayPet dPet = new DisplayPet(pet);
            dPet.setVisits(this.visitRepository.findByPetId(pet.getId()));
            dOwner.getPets().add(dPet);
        });
        return dOwner;
    }


}
