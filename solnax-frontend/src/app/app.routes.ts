import { Routes } from '@angular/router';
import {HouseOverview} from "./components/house-overview/house-overview";

export const routes: Routes = [
  { path: 'map', component: HouseOverview },
  { path: '', redirectTo: '/map', pathMatch: 'full' }, // Default route
  { path: '**', redirectTo: '/map' } // Wildcard (404) route
];
