# LightEnderChest

---

### › Features

- Purchase of slots
- Custom and customizable enderchest
- Setting the price for each slot _or_ specify the **price-step** for slots without a price
- Inventory management and statistics in the data menu
- Support for Vault and PlayerPoints
- Support for MiniMessage and Legacy colors
- Full database caching for increased performance.

---

### › Abilities

- Purchase of slots in someone else's inventory _(including without withdrawing funds from the balance)_
- Prohibition on buying slots in someone else's inventory
- Inventory editing is prohibited.

---

### › Screenshots


#### TODO:

---

## › Commands and Permissions
| Command                             | Description                         | Permission                          |
|-------------------------------------|-------------------------------------|-------------------------------------|
| lightenderchest                     | Open an enderchest                  | lightenderchest.use                 |
| lightenderchest clear               | Clear an enderchest                 | lightenderchest.clear               |
|                                     |                                     |                                     |
| lightenderchest \<username>         | Open someone else's inventory       | lightenderchest.admin.view          |
| lightenderchest open \<username>    | Open the enderchest to the player   | lightenderchest.admin.open          |
| lightenderchest close \<username>   | Close the enderchest to the player  | lightenderchest.admin.close         |
| lightenderchest clear \<username>   | Clear an enderchest                 | lightenderchest.admin.clear         |
| lightenderchest reset \<username>   | Reset the enderchest to the player  | lightenderchest.admin.reset         |
|                                     |                                     |                                     |
| lightenderchest cache data          | Open the cache data menu            | lightenderchest.cache.data          |
| lightenderchest cache refresh       | Refresh the cache from the database | lightenderchest.cache.refresh       |
| lightenderchest cache purge         | Remove old data from the cache      | lightenderchest.cache.purge         |
| lightenderchest cache clear-online  | Clear the cache for online players  | lightenderchest.cache.clear-online  |
| lightenderchest cache clear-offline | Clear the cache for offline players | lightenderchest.cache.clear-offline |
| lightenderchest cache clear         | Clear the entire cache              | lightenderchest.cache.clear         |
|                                     |                                     |                                     |
| lightenderchest reload              | Reload all configurations           | lightenderchest.reload              |

| Permission                 | Description                        |
|----------------------------|------------------------------------|
| lightenderchest.admin.edit | Edit other enderchests             |
| lightenderchest.admin.buy  | Buying slots in other enderchests  |
| lightenderchest.admin.*    | Complete management of enderchests |
| lightenderchest.*          | Full access                        |
